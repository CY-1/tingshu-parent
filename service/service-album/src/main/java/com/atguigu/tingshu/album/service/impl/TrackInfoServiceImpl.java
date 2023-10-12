package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackStatMapper;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.album.TrackStat;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.atguigu.tingshu.vo.album.TrackMediaInfoVo;
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

import java.math.BigDecimal;

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
    private AlbumInfoMapper albumInfoMapper;
    @Override
    public void saveTrackInfo(TrackInfoVo trackInfoVo, Long userId) {
        TrackInfo trackInfo = new TrackInfo();
        BeanUtils.copyProperties(trackInfoVo,trackInfo);
        trackInfo.setUserId(AuthContextHolder.getUserId());
        trackInfo.setStatus(SystemConstant.TRACK_STATUS_PASS);
        trackInfo.setSource("1");
        // 获取流媒体信息.
        TrackMediaInfoVo trackMediaInfo = vodService.getTrackMediaInfo(trackInfoVo.getMediaFileId());
        trackInfo.setMediaSize(trackMediaInfo.getSize());
        trackInfo.setMediaUrl(trackMediaInfo.getMediaUrl());
        trackInfo.setMediaDuration(BigDecimal.valueOf(trackMediaInfo.getDuration()));
        trackInfo.setMediaType(trackMediaInfo.getType());
        // 获取上一条声音
        TrackInfo preTrackInfo = this.getOne(new LambdaQueryWrapper<TrackInfo>().eq(TrackInfo::getAlbumId, trackInfoVo.getAlbumId()).orderByDesc(TrackInfo::getId).select(TrackInfo::getOrderNum).last("limit 1"));
        int orderNum = 1;
        if(null != preTrackInfo) {
            orderNum = preTrackInfo.getOrderNum() + 1;
        }
        trackInfo.setOrderNum(orderNum);
        this.save(trackInfo);
        //更新专辑声音总数
        albumInfoMapper.update(null,new UpdateWrapper<AlbumInfo>().
                eq("id",trackInfoVo.getAlbumId()).setSql("include_track_count=include_track_count+1")
        );
        // 初始化统计数据
        this.saveTrackStat(trackInfo.getId(),SystemConstant.TRACK_STAT_PLAY);
        this.saveTrackStat(trackInfo.getId(),SystemConstant.TRACK_STAT_COLLECT);
        this.saveTrackStat(trackInfo.getId(),SystemConstant.TRACK_STAT_COMMENT);
    }

    @Override
    public IPage<TrackListVo> findUserTrackPage(Page<TrackListVo> trackListVoPage, TrackInfoQuery trackInfoQuery) {
//	调用mapper层方法
        return trackInfoMapper.selectUserTrackPage(trackListVoPage,trackInfoQuery);
    }

    @Override
    public void updateTrackInfo(Long id, TrackInfoVo trackInfoVo) {
        TrackInfo trackInfo = new TrackInfo();
        BeanUtils.copyProperties(trackInfoVo,trackInfo);
        trackInfo.setIsOpen(trackInfoVo.getIsOpen());
        trackInfoMapper.update(trackInfo,new QueryWrapper<TrackInfo>().eq("id",id));
    }

    private void saveTrackStat(Long trackId, String trackType) {
        TrackStat trackStat = new TrackStat();
        trackStat.setTrackId(trackId);
        trackStat.setStatType(trackType);
        trackStat.setStatNum(0);
        this.trackStatMapper.insert(trackStat);
    }
}
