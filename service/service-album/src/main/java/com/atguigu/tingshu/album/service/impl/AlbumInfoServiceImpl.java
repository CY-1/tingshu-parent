package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.mapper.AlbumAttributeValueMapper;
import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.AlbumStatMapper;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.common.constant.KafkaConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.service.KafkaService;
import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.AlbumStat;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumAttributeValueVo;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class AlbumInfoServiceImpl extends ServiceImpl<AlbumInfoMapper, AlbumInfo> implements AlbumInfoService {

	@Autowired
	private AlbumInfoMapper albumInfoMapper;
    @Autowired
    private AlbumAttributeValueMapper albumAttributeValueMapper;
    @Autowired
    private AlbumStatMapper albumStatMapper;
    @Autowired
    private KafkaService kafkaService;
    /**
     * 保存专辑方法
     * @param albumInfoVo
     * @param userId -- 可以暂时写个固定值
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAlbumInfo(AlbumInfoVo albumInfoVo, Long userId) {
        //	创建专辑对象
        AlbumInfo albumInfo = new AlbumInfo();
        //	属性拷贝
        BeanUtils.copyProperties(albumInfoVo,albumInfo);
        //	设置专辑审核状态为：通过
        albumInfo.setStatus(SystemConstant.ALBUM_STATUS_PASS);
        //	设置用户Id
        albumInfo.setUserId(userId);
        //  付费的默认前前5集免费试看
        if(!SystemConstant.ALBUM_PAY_TYPE_FREE.equals(albumInfo.getPayType())) {
            albumInfo.setTracksForFree(5);
        }
        //	保存专辑
        this.save(albumInfo);

        //	保存专辑属性值：
        List<AlbumAttributeValueVo> albumAttributeValueVoList = albumInfoVo.getAlbumAttributeValueVoList();
        //	判断
        if (!CollectionUtils.isEmpty(albumAttributeValueVoList)){
            //	循环遍历设置字段值
            albumAttributeValueVoList.stream().forEach(albumAttributeValueVo -> {
                AlbumAttributeValue albumAttributeValue = new AlbumAttributeValue();
                BeanUtils.copyProperties(albumAttributeValueVo, albumAttributeValue);
                albumAttributeValue.setAlbumId(albumInfo.getId());
                //	保存数据
                albumAttributeValueMapper.insert(albumAttributeValue);
            });
        }

        //初始化统计数据
        this.saveTrackStat(albumInfo.getId(), SystemConstant.ALBUM_STAT_PLAY);
        this.saveTrackStat(albumInfo.getId(), SystemConstant.ALBUM_STAT_SUBSCRIBE);
        this.saveTrackStat(albumInfo.getId(), SystemConstant.ALBUM_STAT_BROWSE);
        this.saveTrackStat(albumInfo.getId(), SystemConstant.ALBUM_STAT_COMMENT);


        //	发送kafka上架消息
        if ("1".equals(albumInfo.getIsOpen())){
            this.kafkaService.sendMessage(KafkaConstant.QUEUE_ALBUM_UPPER,String.valueOf(albumInfo.getId()));
        }
    }

    @Override
    public IPage<AlbumListVo> findUserAlbumPage(Page<AlbumListVo> albumInfoPage, AlbumInfoQuery albumInfoQuery) {


        return albumInfoMapper.selectUserAlbumPage(albumInfoPage,albumInfoQuery);
    }

    @Override
    public void removeAlbumInfoById(Long id) {
        this.update(new UpdateWrapper<AlbumInfo>().eq("id",id).set("is_deleted",1));

        albumAttributeValueMapper.update(null,new UpdateWrapper<AlbumAttributeValue>().eq("album_id",id).set("is_deleted",1));

        albumStatMapper.update(null,new UpdateWrapper<AlbumStat>().eq("album_id",id).set("is_deleted",1));
        //下架
        kafkaService.sendMessage(KafkaConstant.QUEUE_ALBUM_LOWER, String.valueOf(id));
    }

    @Override
    public AlbumInfo getAlbumInfoById(Long id) {
        AlbumInfo albuminfo = this.getById(id);
        if(albuminfo!=null){
            List<AlbumAttributeValue> list = albumAttributeValueMapper.selectList(
                    new QueryWrapper<AlbumAttributeValue>().eq("album_id",id));
            albuminfo.setAlbumAttributeValueVoList(list);
        }
        return albuminfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAlbumInfo(Long id, AlbumInfoVo albumInfoVo) {
        AlbumInfo albumInfo = this.getById(id);
        BeanUtils.copyProperties(albumInfoVo, albumInfo);
        //	根据id 修改数据
        this.updateById(albumInfo);

        //	先删除专辑属性数据
        albumAttributeValueMapper.delete(new LambdaQueryWrapper<AlbumAttributeValue>().eq(AlbumAttributeValue::getAlbumId,id));

        //	保存专辑属性数据
        List<AlbumAttributeValueVo> albumAttributeValueVoList = albumInfoVo.getAlbumAttributeValueVoList();
        if (!CollectionUtils.isEmpty(albumAttributeValueVoList)){
            albumAttributeValueVoList.forEach(albumAttributeValueVo -> {
                //	创建专辑属性对象
                AlbumAttributeValue albumAttributeValue = new AlbumAttributeValue();
                //	进行数据拷贝
                BeanUtils.copyProperties(albumAttributeValueVo,albumAttributeValue);
                //	赋值专辑属性Id
                albumAttributeValue.setAlbumId(id);
                albumAttributeValueMapper.insert(albumAttributeValue);
            });
        }
        //	更新上架下架
        if("1".equals(albumInfo.getIsOpen())) {
            kafkaService.sendMessage(KafkaConstant.QUEUE_ALBUM_UPPER, String.valueOf(albumInfo.getId()));
        } else {
            kafkaService.sendMessage(KafkaConstant.QUEUE_ALBUM_LOWER, String.valueOf(albumInfo.getId()));
        }
    }

    @Override
    public List<AlbumInfo> findUserAllAlbumList(Long userId) {
        userId = userId==null?1:userId;
        List<AlbumInfo> list = this.list(new QueryWrapper<AlbumInfo>().eq("user_id",userId).orderByAsc("id"));
        return list;
    }

    @Override
    public List<AlbumAttributeValue> findAlbumAttributeValueByAlbumId(Long albumId) {
        LambdaQueryWrapper<AlbumAttributeValue> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(AlbumAttributeValue::getAlbumId,albumId);
        List<AlbumAttributeValue> albumAttributeValueList = albumAttributeValueMapper.selectList(lambdaQueryWrapper);
        //	返回集合数据
        return albumAttributeValueList;
    }

    @Override
    public AlbumStatVo getAlbumStatVoByAlbumId(Long albumId) {
        //	调用mapper 层方法
        return albumInfoMapper.selectAlbumStat(albumId);

    }

    @Override
    public void updateStat(Long albumId, String statType, Integer count) {
        // 更新数据
        albumInfoMapper.updateStat(albumId, statType, count);
    }

    /**
     * 初始化统计数据
     * @param albumId
     * @param statType
     */
    private void saveTrackStat(Long albumId, String statType) {
        AlbumStat albumStat = new AlbumStat();
        albumStat.setAlbumId(albumId);
        albumStat.setStatType(statType);
        albumStat.setStatNum(0);
        albumStatMapper.insert(albumStat);
    }
}
