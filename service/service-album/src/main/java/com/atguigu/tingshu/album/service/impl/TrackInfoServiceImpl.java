package com.atguigu.tingshu.album.service.impl;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.atguigu.tingshu.album.config.VodConstantProperties;
import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackStatMapper;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.album.TrackStat;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.user.client.UserListenProcessFeignClient;
import com.atguigu.tingshu.vo.album.*;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class TrackInfoServiceImpl extends ServiceImpl<TrackInfoMapper, TrackInfo> implements TrackInfoService {

	@Autowired
	private TrackInfoMapper trackInfoMapper;
    @Autowired
    private VodService vodService;
    @Autowired
    private TrackStatMapper trackStatMapper;
    @Autowired
    private VodConstantProperties vodConstantProperties;
    @Autowired
    private AlbumInfoMapper albumInfoMapper;
    @Autowired
    private UserInfoFeignClient userInfoFeignClient;
    @Autowired
    private AlbumInfoService albumInfoService;
    @Autowired
    private UserListenProcessFeignClient userListenProcessFeignClient;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveTrackInfo(TrackInfoVo trackInfoVo) {

        // 1.保存声音表
        TrackInfo trackInfo = new TrackInfo();
        BeanUtils.copyProperties(trackInfoVo, trackInfo);
        // 获取userId
        Long userId = AuthContextHolder.getUserId();
        trackInfo.setUserId(userId == null ? 1 : userId);
        // 设置声音排列序号: 先查询
        TrackInfo preTrackInfo = this.getOne(new LambdaQueryWrapper<TrackInfo>()
                .eq(TrackInfo::getAlbumId, trackInfoVo.getAlbumId())
                .orderByDesc(TrackInfo::getOrderNum)
                .select(TrackInfo::getOrderNum).last("limit 1"));
        // 如果当前声音是第一个则取1，否则取前一个声音的序号加1
        Integer orderNum = preTrackInfo == null ? 1 : preTrackInfo.getOrderNum() + 1;
        trackInfo.setOrderNum(orderNum);
        // 设置来源
        trackInfo.setSource(SystemConstant.TRACK_SOURCE_USER);
        // 设置状态
        trackInfo.setStatus(SystemConstant.TRACK_STATUS_PASS);
        // 获取媒介信息
        TrackMediaInfoVo mediaInfo = this.vodService.getMediaInfo(trackInfoVo.getMediaFileId());
        // 设置媒体信息相关参数
        trackInfo.setMediaSize(mediaInfo.getSize());
        trackInfo.setMediaType(mediaInfo.getType());
        trackInfo.setMediaDuration(new BigDecimal(mediaInfo.getDuration()));
        trackInfo.setMediaUrl(mediaInfo.getMediaUrl());
        this.save(trackInfo);
        Long trackInfoId = trackInfo.getId();

        // 2.更新专辑表
        // 查询专辑信息
        AlbumInfo albumInfo = this.albumInfoMapper.selectById(trackInfoVo.getAlbumId());
        albumInfo.setIncludeTrackCount(albumInfo.getIncludeTrackCount() + 1);
        this.albumInfoMapper.updateById(albumInfo);

        // 3.保存声音统计表
        initTrackStat(trackInfoId, SystemConstant.TRACK_STAT_PLAY);
        initTrackStat(trackInfoId, SystemConstant.TRACK_STAT_COLLECT);
        initTrackStat(trackInfoId, SystemConstant.TRACK_STAT_PRAISE);
        initTrackStat(trackInfoId, SystemConstant.TRACK_STAT_COMMENT);
    }
    private void initTrackStat(Long trackInfoId, String statType) {
        TrackStat trackStat = new TrackStat();
        trackStat.setStatType(statType);
        trackStat.setStatNum(0);
        trackStat.setTrackId(trackInfoId);
        this.trackStatMapper.insert(trackStat);
    }
    @Override
    public IPage<TrackListVo> findUserTrackPage(Page<TrackListVo> trackListVoPage, TrackInfoQuery trackInfoQuery) {
//	调用mapper层方法
        return trackInfoMapper.selectUserTrackPage(trackListVoPage,trackInfoQuery);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateTrackInfo(Long trackId, TrackInfoVo trackInfoVo) {
        TrackInfo trackInfo = this.getById(trackId);
        // 在copy之前先获取数据库中的媒体文件的id
        String oldMediaFileId = trackInfo.getMediaFileId();
        BeanUtils.copyProperties(trackInfoVo, trackInfo);
        // 本次更新后的媒体文件id
        String mediaFileId = trackInfoVo.getMediaFileId();
        if (!StringUtils.equals(oldMediaFileId, mediaFileId)) {
            TrackMediaInfoVo mediaInfo = this.vodService.getMediaInfo(mediaFileId);
            if (mediaInfo != null) {
                trackInfo.setMediaUrl(mediaInfo.getMediaUrl());
                trackInfo.setMediaType(mediaInfo.getType());
                trackInfo.setMediaSize(mediaInfo.getSize());
                trackInfo.setMediaDuration(new BigDecimal(mediaInfo.getDuration()));
            }
        }

        this.updateById(trackInfo);
    }

    @Override
    @Transactional  //因为专辑内数目减一 有并发问题
    public void removeTrackInfo(Long id) {
        TrackInfo removed = this.trackInfoMapper.selectOne(new QueryWrapper<TrackInfo>().eq("id", id));
        this.trackInfoMapper.delete(new QueryWrapper<TrackInfo>().eq("id",id));
        this.trackStatMapper.delete(new QueryWrapper<TrackStat>().eq("track_id",id));
        this.albumInfoMapper.update(null,
                new UpdateWrapper<AlbumInfo>().eq("id",removed.getAlbumId()).setSql("include_track_count=include_track_count-1"));
        //删除声音媒体
        vodService.removeTrack(removed.getMediaFileId());
    }

    @Override
    public IPage<AlbumTrackListVo> findAlbumTrackPage(Page<AlbumTrackListVo> pageParam, Long albumId, Long userId) {
        //	根据专辑Id 获取到专辑
        IPage<AlbumTrackListVo> pageInfo = trackInfoMapper.selectAlbumTrackPage(pageParam,albumId);

        //	判断用户是否需要付费：0101-免费 0102-vip付费 0103-付费
        AlbumInfo albumInfo = albumInfoService.getById(albumId);
        Assert.notNull(albumInfo,"专辑对象不能为空");
        //	判断用户是否登录
        if (null == userId){
            //	除免费的都需要显示付费表示
            if (!SystemConstant.ALBUM_PAY_TYPE_FREE.equals(albumInfo.getPayType())){
                //	处理试听声音，获取需要付费的声音列表

                List<AlbumTrackListVo> albumTrackNeedPaidListVoList = pageInfo.getRecords().stream().filter(albumTrackListVo -> albumTrackListVo.getOrderNum().intValue() > albumInfo.getTracksForFree()).collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(albumTrackNeedPaidListVoList)){
                    albumTrackNeedPaidListVoList.forEach(albumTrackListVo -> {
                        //	显示付费通知
                        albumTrackListVo.setIsShowPaidMark(true);
                    });
                }
            }
        } else {
            //	用户已登录
            //	声明变量是否需要付费，默认不需要付费
            boolean isNeedPaid = false;
            //  vip 付费情况
            if (SystemConstant.ALBUM_PAY_TYPE_VIPFREE.equals(albumInfo.getPayType())){
                //	获取用户信息
                Result<UserInfoVo> userInfoVoResult = userInfoFeignClient.getUserInfoVo(userId);
                Assert.notNull(userInfoVoResult,"用户信息不能为空");
                UserInfoVo userInfoVo = userInfoVoResult.getData();
                //	1.	VIP 免费,如果不是vip则需要付费，将这个变量设置为true，需要购买
                if (userInfoVo.getIsVip().intValue() == 0){
                    isNeedPaid = true;
                }
                //1.1 如果是vip但是vip过期了（定时任务还为更新状态）
                if(userInfoVo.getIsVip().intValue() == 1 && userInfoVo.getVipExpireTime().before(new Date())) {
                    isNeedPaid = true;
                }
            } else if (SystemConstant.ALBUM_PAY_TYPE_REQUIRE.equals(albumInfo.getPayType())){
                //	2.	付费
                isNeedPaid = true;
            }
            //需要付费，判断用户是否购买过专辑或声音
            if(isNeedPaid) {
                //	处理试听声音，获取需要付费的声音列表
                List<AlbumTrackListVo> albumTrackNeedPaidListVoList = pageInfo.getRecords().stream().filter(albumTrackListVo -> albumTrackListVo.getOrderNum().intValue() > albumInfo.getTracksForFree()).collect(Collectors.toList());
                //	判断
                if (!CollectionUtils.isEmpty(albumTrackNeedPaidListVoList)){
                    //	判断用户是否购买该声音
                    //	获取到声音Id 集合列表
                    List<Long> trackIdList = albumTrackNeedPaidListVoList.stream().map(AlbumTrackListVo::getTrackId).collect(Collectors.toList());
                    //	获取用户购买的声音列表
                    Result<Map<Long, Integer>> mapResult = userInfoFeignClient.userIsPaidTrack(albumId,trackIdList);
                    Assert.notNull(mapResult,"声音集合不能为空.");
                    Map<Long, Integer> map = mapResult.getData();
                    Assert.notNull(map,"map集合不能为空.");
                    albumTrackNeedPaidListVoList.forEach(albumTrackListVo -> {
                        if (!map.containsKey(albumTrackListVo.getTrackId())){
                            // 显示付费
                            albumTrackListVo.setIsShowPaidMark(false);
                        } else {
                            //	如果map.get(albumTrackListVo.getTrackId()) == 1 已经购买过，则不显示付费标识;
                            boolean isBuy = map.get(albumTrackListVo.getTrackId()) == 1 ? false : true;
                            albumTrackListVo.setIsShowPaidMark(isBuy);
                        }
                    });
                }
            }
        }
        // 返回集合数据
        return pageInfo;
    }

    @Override
    public void updateStat(Long albumId, Long trackId, String statType, Integer count) {
        //	更新统计数据
        trackInfoMapper.updateStat(trackId, statType, count);
        //	更新评论数
        if(statType.equals(SystemConstant.TRACK_STAT_COMMENT)) {
            albumInfoService.updateStat(albumId, SystemConstant.ALBUM_STAT_COMMENT, count);
        }
        //	更新播放量
        if(statType.equals(SystemConstant.TRACK_STAT_PLAY)) {
            albumInfoService.updateStat(albumId, SystemConstant.ALBUM_STAT_PLAY, count);
        }

    }

    @Override
    public TrackStatVo getTrackStatVoByTrackId(Long trackId) {
        //	调用mapper 层方法
        return trackInfoMapper.selectTrackStat(trackId);
    }

    @Override
    public Map<String, Object> getPlayToken(Long userId, Long trackId) {
        TrackInfo trackInfo = this.getById(trackId);
        Assert.notNull(trackInfo,"声音对象不能为空");
        AlbumInfo albumInfo = albumInfoService.getById(trackInfo.getAlbumId());
        Assert.notNull(trackInfo,"专辑对象不能为空");

        //	判断用户是否需要付费
        if (null == userId){
            //	除免费的都需要显示付费标识
            if(!SystemConstant.ALBUM_PAY_TYPE_FREE.equals(albumInfo.getPayType()) && trackInfo.getOrderNum().intValue() > albumInfo.getTracksForFree().intValue()) {
                throw new GuiguException(ResultCodeEnum.NO_BUY_NOT_SEE);
            }
        }else {
            boolean isNeedPaid = false;
            if(SystemConstant.ALBUM_PAY_TYPE_VIPFREE.equals(albumInfo.getPayType())) {
                //获取用户信息
                Result<UserInfoVo> userInfoVoResult = userInfoFeignClient.getUserInfoVo(userId);
                Assert.notNull(userInfoVoResult,"用户对象不能为空");
                UserInfoVo userInfoVo = userInfoVoResult.getData();

                //1，VIP免费（非VIP观看）
                if(userInfoVo.getIsVip().intValue() == 0) {
                    isNeedPaid = true;
                }
                //1.1 如果是vip但是vip过期了（定时任务还为更新状态）
                if(userInfoVo.getIsVip().intValue() == 1 && userInfoVo.getVipExpireTime().before(new Date())) {
                    isNeedPaid = true;
                }
            } else if(SystemConstant.ALBUM_PAY_TYPE_REQUIRE.equals(albumInfo.getPayType())) {
                //2，付费类型的
                isNeedPaid = true;
            } else {
                isNeedPaid = false;
            }
            //需要付费，判断用户是否购买过专辑或声音
            if(isNeedPaid) {
                //	处理试听声音，判断当前声音是否包含在免费试听范围
                if(trackInfo.getOrderNum().intValue() > albumInfo.getTracksForFree().intValue()) {
                    //判断用户是否购买该声音
                    List<Long> tarckIdList = Arrays.asList(trackId);
                    Result<Map<Long, Integer>> mapResult = userInfoFeignClient.userIsPaidTrack(albumInfo.getId(), tarckIdList);
                    Assert.notNull(mapResult,"map 集合不能为空");
                    Map<Long, Integer> map = mapResult.getData();
                    // 未购买
                    if(map.get(trackId) == 0 ) {
                        throw new GuiguException(ResultCodeEnum.NO_BUY_NOT_SEE);
                    }
                }
            }
        }
        Result<BigDecimal> result = this.userListenProcessFeignClient.getTrackBreakSecond(trackId);
        Assert.notNull(result,"获取到跳出时间结果不能为空");
        BigDecimal breakSecond = result.getData();
        String playToken = vodService.getPlayToken(trackInfo.getMediaFileId());
        Map<String, Object> map = new HashMap<>();
        map.put("playToken", playToken);
        map.put("mediaFileId", trackInfo.getMediaFileId());
        map.put("breakSecond", breakSecond);
        map.put("appId", vodConstantProperties.getAppId());

        //获取下一个播放声音
        LambdaQueryWrapper<TrackInfo> queryWrapper = new LambdaQueryWrapper<TrackInfo>();
        queryWrapper.eq(TrackInfo::getAlbumId, trackInfo.getAlbumId());
        queryWrapper.gt(TrackInfo::getOrderNum, trackInfo.getOrderNum());
        queryWrapper.orderByAsc(TrackInfo::getOrderNum);
        queryWrapper.select(TrackInfo::getId);
        queryWrapper.last("limit 1");
        TrackInfo nextTrackInfo = this.getOne(queryWrapper);
        if(null != nextTrackInfo) {
            map.put("nextTrackId", nextTrackInfo.getId());
        } else {
            map.put("nextTrackId", 0L);
        }
        return map;
    }


}
