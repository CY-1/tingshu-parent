package com.atguigu.tingshu.search.receiver;

import com.atguigu.tingshu.common.constant.KafkaConstant;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author atguigu-mqx
 * @ClassName SearchReceiver
 * @description: TODO
 * @version: 1.0
 */
@Component
@Slf4j
public class SearchReceiver {

    @Autowired
    private SearchService searchService;

    /**
     * 监听专辑上架
     * @param consumerRecord
     */
    @KafkaListener(topics = KafkaConstant.QUEUE_ALBUM_UPPER)
    public void upperGoods(ConsumerRecord<String,String> consumerRecord){
        //  获取到发送的消息
        Long albumId = Long.parseLong(consumerRecord.value());
        if (null != albumId){
            searchService.upperAlbum(albumId);
        }
    }

    /**
     * 监听专辑下架
     * @param consumerRecord
     */
    @KafkaListener(topics = KafkaConstant.QUEUE_ALBUM_LOWER)
    public void lowerGoods(ConsumerRecord<String,String> consumerRecord){
        //  获取到发送的消息
        Long albumId = Long.parseLong(consumerRecord.value());
        if (null != albumId){
            searchService.lowerAlbum(albumId);
        }
    }
    /**
     * 根据一级分类Id获取数据
     * @param category1Id
     * @return
     */
    @Operation(summary = "获取频道页数据")
    @GetMapping("channel/{category1Id}")
    public Result channel(@PathVariable Long category1Id) throws IOException {

        //  调用服务层方法
        List<Map<String, Object>> mapList = null;
        mapList = searchService.channel(category1Id);
        return Result.ok(mapList);
    }
}