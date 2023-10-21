package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumTrackListVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.atguigu.tingshu.vo.album.TrackStatVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.security.core.parameters.P;

@Mapper
public interface TrackInfoMapper extends BaseMapper<TrackInfo> {


    IPage<TrackListVo> selectUserTrackPage(Page<TrackListVo> trackListVoPage,@Param("vo") TrackInfoQuery trackInfoQuery);
    /**
     * 获取专辑声音列表
     * @param pageParam
     * @param albumId
     * @return
     */
    IPage<AlbumTrackListVo> selectAlbumTrackPage(Page<AlbumTrackListVo> pageParam,@Param("albumId") Long albumId);

    void updateStat(@Param("trackId") Long trackId,@Param("stateType") String statType,@Param("count") Integer count);

    TrackStatVo selectTrackStat(@Param("trackId") Long trackId);
}
