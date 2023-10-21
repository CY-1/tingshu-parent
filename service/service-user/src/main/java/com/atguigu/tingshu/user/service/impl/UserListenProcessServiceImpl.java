package com.atguigu.tingshu.user.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.common.constant.KafkaConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.service.KafkaService;
import com.atguigu.tingshu.common.util.MongoUtil;
import com.atguigu.tingshu.model.user.UserListenProcess;
import com.atguigu.tingshu.user.service.UserListenProcessService;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import com.atguigu.tingshu.vo.user.UserListenProcessVo;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class UserListenProcessServiceImpl implements UserListenProcessService {

	@Autowired
	private MongoTemplate mongoTemplate;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private KafkaService kafkaService;
    @Override
    public BigDecimal getTrackBreakSecond(Long userId, Long trackId) {

        //	根据用户Id,声音Id获取播放进度对象
        Query query = Query.query(Criteria.where("userId").is(userId).and("trackId").is(trackId));
        UserListenProcess userListenProcess = mongoTemplate.findOne(query, UserListenProcess.class, MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS, userId));
        //	判断
        if (null != userListenProcess){
            //	获取到播放的跳出时间
            return userListenProcess.getBreakSecond();
        }
        return new BigDecimal("0");
    }

    @Override
    public void updateListenProcess(Long userId, UserListenProcessVo userListenProcessVo) {
        // 根据用户Id，声音Id 设置查询条件
        Query query = Query.query(Criteria.where("userId").is(userId).and("trackId").is(userListenProcessVo.getTrackId()));
        UserListenProcess userListenProcess = this.mongoTemplate.findOne(query, UserListenProcess.class, MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS, userId));
        //	判断
        if (null != userListenProcess){
            //	设置更新时间
            userListenProcess.setUpdateTime(new Date());
            //	设置跳出时间
            userListenProcess.setBreakSecond(userListenProcessVo.getBreakSecond());
            //	存储数据
            mongoTemplate.save(userListenProcess,MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS,userId));
        } else {
            //	创建对象
            userListenProcess = new UserListenProcess();
            //	进行属性拷贝
            BeanUtils.copyProperties(userListenProcessVo, userListenProcess);
            //	设置Id
            userListenProcess.setId(ObjectId.get().toString());
            //	设置用户Id
            userListenProcess.setUserId(userId);
            //	设置是否显示
            userListenProcess.setIsShow(1);
            //	创建时间
            userListenProcess.setCreateTime(new Date());
            //	更新时间
            userListenProcess.setUpdateTime(new Date());
            //	保存数据
            mongoTemplate.save(userListenProcess, MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS, userId));
        }

        //	设置 设置这个key表示今天听过了多少次 但是只增加一次
        String key = "user:track:" + new DateTime().toString("yyyyMMdd") + ":" + userListenProcessVo.getTrackId();
        //	通过声音Id，用户Id 两个维度判断
        Boolean isExist = redisTemplate.opsForValue().getBit(key, userId);
        if (!isExist){
            //	将数据写入缓存
            redisTemplate.opsForValue().setBit(key, userId, true);
            //	设置key 的过期时间
            LocalDateTime localDateTime = LocalDateTime.now().plusDays(1).plusHours(0).plusMinutes(0).plusSeconds(0).plusNanos(0);
            //  获取相差的秒数
            long seconds = ChronoUnit.SECONDS.between(LocalDateTime.now(),localDateTime);
            //	将数据存储到缓存，并设置过期时间
            this.redisTemplate.opsForValue().setIfAbsent(key,userId,seconds, TimeUnit.SECONDS);

            TrackStatMqVo trackStatMqVo = new TrackStatMqVo();
            // 之后放在redis中 解决幂等性问题
            trackStatMqVo.setBusinessNo(UUID.randomUUID().toString().replaceAll("-",""));
            trackStatMqVo.setAlbumId(userListenProcessVo.getAlbumId());
            trackStatMqVo.setTrackId(userListenProcessVo.getTrackId());
            trackStatMqVo.setStatType(SystemConstant.TRACK_STAT_PLAY);
            trackStatMqVo.setCount(1);
            //	发送消息 更新track统计数据
            kafkaService.sendMessage(KafkaConstant.QUEUE_TRACK_STAT_UPDATE, JSON.toJSONString(trackStatMqVo));
        }
    }

    @Override
    public Map<String, Object> getLatelyTrack(Long userId) {
        Query query = Query.query(Criteria.where("userId").is(userId));
        Sort sort = Sort.by(Sort.Direction.DESC, "updateTime");
        query.with(sort);
        UserListenProcess userListenProcess = mongoTemplate.findOne(query, UserListenProcess.class, MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS, userId));
        if(null == userListenProcess) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("albumId", userListenProcess.getAlbumId());
        map.put("trackId", userListenProcess.getTrackId());
        return map;

    }
}
