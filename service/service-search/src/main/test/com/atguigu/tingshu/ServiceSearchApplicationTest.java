package com.atguigu.tingshu;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class ServiceSearchApplicationTest {
    @Autowired
    private ElasticsearchClient elasticsearchClient;
    @Test
    @SneakyThrows
    void my1(){
//        System.out.println(elasticsearchClient);
        SearchResponse<AlbumInfoIndex> albuminfo = elasticsearchClient.search(s -> s.index("albuminfo"),
                AlbumInfoIndex.class);
        System.out.println(albuminfo);
        System.out.println("=========");
    }


}