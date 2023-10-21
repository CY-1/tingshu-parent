package com.atguigu.tingshu;

import com.atguigu.tingshu.common.constant.KafkaConstant;
import com.atguigu.tingshu.common.service.KafkaService;
import com.atguigu.tingshu.search.client.SearchFeignClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class ServiceSearchApplicationTest {
    @Autowired
    private KafkaService kafkaService;
    @Test
    void testUpper(){
        for(int i=0;i<1607;i++)
        this.kafkaService.sendMessage(KafkaConstant.QUEUE_ALBUM_UPPER,String.valueOf(i));
    }
}