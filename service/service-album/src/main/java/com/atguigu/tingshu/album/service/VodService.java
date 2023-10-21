package com.atguigu.tingshu.album.service;

import com.atguigu.tingshu.vo.album.TrackMediaInfoVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface VodService {

    Map<String, Object> uploadTrack(MultipartFile file);

    TrackMediaInfoVo getTrackMediaInfo(String mediaFileId);

    void removeTrack(String mediaFileId);

    TrackMediaInfoVo getMediaInfo(String mediaFileId);

    String getPlayToken(String mediaFileId);
}
