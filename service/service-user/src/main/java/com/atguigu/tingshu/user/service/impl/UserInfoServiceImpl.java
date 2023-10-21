package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.model.user.UserPaidAlbum;
import com.atguigu.tingshu.model.user.UserPaidTrack;
import com.atguigu.tingshu.user.mapper.UserInfoMapper;
import com.atguigu.tingshu.user.mapper.UserPaidAlbumMapper;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.user.service.UserPaidTrackService;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

	@Autowired
	private UserInfoMapper userInfoMapper;
    @Autowired
    private UserPaidAlbumMapper userPaidAlbumMapper;
    @Autowired
    private UserPaidTrackService userPaidTrackService;
    @Override
    public UserInfoVo getUserInfoVoByUserId(Long userId) {
        UserInfoVo userInfoVo = new UserInfoVo();
        UserInfo userInfo = this.userInfoMapper.selectById(userId);
        BeanUtils.copyProperties(userInfo,userInfoVo);
        return userInfoVo;
    }

    @Override
    public Map<Long, Integer> userIsPaidTrack(Long userId, Long albumId, List<Long> trackIdList) {
        //	根据UserId,albumId 获取到当前专辑
        LambdaQueryWrapper<UserPaidAlbum> userPaidAlbumLambdaQueryWrapper = new LambdaQueryWrapper<>();
        userPaidAlbumLambdaQueryWrapper.eq(UserPaidAlbum::getUserId,userId).eq(UserPaidAlbum::getAlbumId,albumId);
        UserPaidAlbum userPaidAlbum = userPaidAlbumMapper.selectOne(userPaidAlbumLambdaQueryWrapper);
        //	判断
        if (null != userPaidAlbum){
            //	创建一个map 集合
            HashMap<Long, Integer> map = new HashMap<>();
            //	如果查询到对应的专辑购买记录，则默认将声音Id 赋值为 1
            trackIdList.forEach(trackId->{
                map.put(trackId,1);
            });
            return map;
        } else {
            //	根据用户Id 与专辑Id 进行查询数据
            LambdaQueryWrapper<UserPaidTrack> userPaidTrackLambdaQueryWrapper = new LambdaQueryWrapper<>();
            userPaidTrackLambdaQueryWrapper.eq(UserPaidTrack::getUserId,userId).in(UserPaidTrack::getTrackId,trackIdList);
            List<UserPaidTrack> userPaidTrackList = userPaidTrackService.list(userPaidTrackLambdaQueryWrapper);
            //	获取到用户购买专辑Id 集合
            List<Long> userPaidTrackIdList = userPaidTrackList.stream().map(UserPaidTrack::getTrackId).collect(Collectors.toList());
            //	创建一个map 集合
            HashMap<Long, Integer> map = new HashMap<>();
            trackIdList.forEach(trackId ->{
                if (userPaidTrackIdList.contains(trackId)){
                    //	用户已购买声音
                    map.put(trackId,1);
                }else {
                    map.put(trackId,0);
                }
            });
            return map;
        }
    }
}
