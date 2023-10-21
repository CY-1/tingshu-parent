package com.atguigu.tingshu.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.album.client.AlbumInfoFeignClient;
import com.atguigu.tingshu.album.client.CategoryFeignClient;
import com.atguigu.tingshu.album.client.TrackInfoFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.search.service.ItemService;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.user.client.UserListenProcessFeignClient;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class ItemServiceImpl implements ItemService {

    @Autowired
    private AlbumInfoFeignClient albumInfoFeignClient;

    @Autowired
    private TrackInfoFeignClient trackInfoFeignClient;

    @Autowired
    private CategoryFeignClient categoryFeignClient;

    @Autowired
    private UserInfoFeignClient userInfoFeignClient;

    @Autowired
    private UserListenProcessFeignClient userListenProcessFeignClient;

//    @Autowired
//    private RedissonClient redissonClient;

//    @Autowired
    private ThreadPoolExecutor threadPoolExecutor=new ThreadPoolExecutor(
            1,1,1, TimeUnit.SECONDS,new ArrayBlockingQueue<>(5)
);

//    @GuiguCache
    @Override
    public Map<String, Object> getItem(Long albumId) {
        Map<String, Object> result = new HashMap<>();

        //远程调用接口之前 提前知道用户访问的专辑id是否存在与布隆过滤器
//        RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(RedisConstant.ALBUM_BLOOM_FILTER);
//        if (!bloomFilter.contains(albumId)) {
//            log.error("用户查询专辑不存在：{}", albumId);
//            //查询数据不存在直接返回空对象
//            return result;
//        }

        /**
         * 说明：异步编排时Feign拦截器（FeignInterceptor类）中 RequestContextHolder.getRequestAttributes()为null
         * 原因：request 信息是存储在 ThreadLocal 中的，所以子线程根本无法获取到主线程的  request 信息。
         * 解决方案：
         *  1、启动类添加@EnableAsync注解
         *  2、在新开子线程之前，将RequestAttributes对象设置为子线程共享，代码如下：
         *      ServletRequestAttributes sra = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
         *      RequestContextHolder.setRequestAttributes(sra, true);
         */
        ServletRequestAttributes sra = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        RequestContextHolder.setRequestAttributes(sra, true);

        // 通过albumId 查询albumInfo
        CompletableFuture<AlbumInfo> albumCompletableFuture = CompletableFuture.supplyAsync(() -> {
            Result<AlbumInfo> albumInfoResult = albumInfoFeignClient.getAlbumInfo(albumId);
            Assert.notNull(albumInfoResult);
            AlbumInfo albumInfo = albumInfoResult.getData();
            // 保存albumInfo
            result.put("albumInfo", albumInfo);
            log.info("albumInfo:{}", JSON.toJSONString(albumInfo));
            return albumInfo;
        }, threadPoolExecutor);

        CompletableFuture<Void> albumStatCompletableFuture = CompletableFuture.runAsync(() -> {
            Result<AlbumStatVo> albumStatVoResult = albumInfoFeignClient.getAlbumStatVo(albumId);
            AlbumStatVo albumStatVo = albumStatVoResult.getData();
            result.put("albumStatVo", albumStatVo);
            log.info("albumStatVo:{}", JSON.toJSONString(albumStatVo));
        }, threadPoolExecutor);

        CompletableFuture<Void> baseCategoryViewCompletableFuture = albumCompletableFuture.thenAcceptAsync(albumInfo -> {
            Result<BaseCategoryView> baseCategoryViewResult = categoryFeignClient.getCategoryView(albumInfo.getCategory3Id());
            BaseCategoryView baseCategoryView = baseCategoryViewResult.getData();
            result.put("baseCategoryView", baseCategoryView);
            log.info("baseCategoryView:{}", JSON.toJSONString(baseCategoryView));
        }, threadPoolExecutor);

        CompletableFuture<Void> announcerCompletableFuture = albumCompletableFuture.thenAcceptAsync(albumInfo -> {
            Result<UserInfoVo> userInfoVoResult = userInfoFeignClient.getUserInfoVo(albumInfo.getUserId());
            UserInfoVo userInfoVo = userInfoVoResult.getData();
            result.put("announcer", userInfoVo);
            log.info("announcer:{}", JSON.toJSONString(userInfoVo));
        }, threadPoolExecutor);

        CompletableFuture.allOf(albumCompletableFuture, albumStatCompletableFuture, baseCategoryViewCompletableFuture, announcerCompletableFuture).join();
        return result;
    }
}
