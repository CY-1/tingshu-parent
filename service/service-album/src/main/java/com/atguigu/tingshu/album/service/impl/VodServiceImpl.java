package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.config.VodConstantProperties;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.vo.album.TrackMediaInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Service
public class VodServiceImpl implements VodService {

    @Autowired
    private VodConstantProperties vodConstantProperties;

    private
    @Autowired
    MinioClient minioClient;
    @Autowired
    private TrackInfoMapper trackInfoMapper;
    @Override
    public Map<String, Object> uploadTrack(MultipartFile file){
        try {
            String url;
            String id = UUID.randomUUID().toString().substring(0,12);
            InputStream inputStream = file.getInputStream();
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket("tingshu")
                    .stream(inputStream, inputStream.available(), -1)
                    .object("voice/" + id + ".mp3")
                    .build();
            minioClient.putObject(putObjectArgs);
            url = "http://192.168.160.130:9001/tingshu/" + "voice/" + id + ".mp3";

            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("mediaFileId",id);
            map.put("mediaUrl",url);
            return map;
        } catch (Exception e) {

        }
        return null;
    }

    @Override
    public TrackMediaInfoVo getTrackMediaInfo(String mediaFileId) {
        TrackMediaInfoVo trackMediaInfoVo = new TrackMediaInfoVo();
        try{
            StatObjectArgs statObjectArgs = StatObjectArgs.builder()
                    .bucket("tingshu")
                    .object("voice/"+mediaFileId+".mp3")
                    .build();
            StatObjectResponse response = minioClient.statObject(statObjectArgs);
            trackMediaInfoVo.setSize(response.size());
            trackMediaInfoVo.setType("mp3");
            trackMediaInfoVo.setDuration((float)10);
            trackMediaInfoVo.setMediaUrl("http://192.168.160.130:9001/tingshu/" + "voice/" +mediaFileId + ".mp3");
        }catch (Exception e){
                e.printStackTrace();
        }
        return trackMediaInfoVo;
    }
}
