package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.config.MinioConstantProperties;
import com.atguigu.tingshu.album.config.VodConstantProperties;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.util.UploadFileUtil;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.vo.album.TrackMediaInfoVo;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qcloud.vod.VodUploadClient;
import com.qcloud.vod.model.VodUploadRequest;
import com.qcloud.vod.model.VodUploadResponse;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tencentcloudapi.vod.v20180717.models.*;
import io.minio.*;
import io.minio.errors.*;
import lombok.SneakyThrows;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Service
public class VodServiceImpl implements VodService {

    @Autowired
    private VodConstantProperties vodConstantProperties;
    @Autowired
    private MinioConstantProperties minioConstantProperties;
    private
    @Autowired
    MinioClient minioClient;
    @Autowired
    private TrackInfoMapper trackInfoMapper;
    @SneakyThrows
    @Override
    public Map<String, Object> uploadTrack(MultipartFile file) {
        // 1.构建文件上传的客户端
        VodUploadClient client = new VodUploadClient(this.vodConstantProperties.getSecretId(), this.vodConstantProperties.getSecretKey());

        // 2.构建请求对象
        VodUploadRequest request = new VodUploadRequest();
        // 构建临时文件路径
        String tempPath = UploadFileUtil.uploadTempPath(this.vodConstantProperties.getTempPath(), file);
        request.setMediaFilePath(tempPath);
        request.setProcedure(this.vodConstantProperties.getProcedure());
//        request.setSubAppId(this.vodConstantProperties.getAppId());

        // 3.通过客户端把请求对象发送到云点播，获取响应结果集
        VodUploadResponse response = client.upload(this.vodConstantProperties.getRegion(), request);

        Map<String, Object> map = new HashMap<>();
        map.put("mediaUrl", response.getMediaUrl());
        map.put("mediaFileId", response.getFileId());
        return map;
    }
//    @Override
//    public Map<String, Object> uploadTrack(MultipartFile file){
//        try {
//            String url;
//            String id = UUID.randomUUID().toString().substring(0,12);
//            InputStream inputStream = file.getInputStream();
//            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
//                    .bucket( minioConstantProperties.getBucketName())
//                    .stream(inputStream, inputStream.available(), -1)
//                    .object("voice/" + id + ".mp3")
//                    .build();
//            minioClient.putObject(putObjectArgs);
//            url = minioConstantProperties.getEndpointUrl()+"/"+minioConstantProperties.getBucketName()
//                    + "/voice/" + id + ".mp3";
//
//            HashMap<String, Object> map = new HashMap<String, Object>();
//            map.put("mediaFileId",id);
//            map.put("mediaUrl",url);
//            return map;
//        } catch (Exception e) {
//
//        }
//        return null;
//    }

    @Override
    public TrackMediaInfoVo getTrackMediaInfo(String mediaFileId) {
        TrackMediaInfoVo trackMediaInfoVo = new TrackMediaInfoVo();
        try{
            StatObjectArgs statObjectArgs = StatObjectArgs.builder()
                    .bucket(minioConstantProperties.getBucketName())
                    .object("voice/"+mediaFileId+".mp3")
                    .build();
            StatObjectResponse response = minioClient.statObject(statObjectArgs);
            trackMediaInfoVo.setSize(response.size());
            trackMediaInfoVo.setType("mp3");
            trackMediaInfoVo.setDuration((float)10);
            trackMediaInfoVo.setMediaUrl(minioConstantProperties.getEndpointUrl()+"/"
                    +minioConstantProperties.getBucketName()+ "/voice/" +mediaFileId + ".mp3");
        }catch (Exception e){
                e.printStackTrace();
        }
        return trackMediaInfoVo;
    }

    @Override
    public void removeTrack(String mediaFileId) {
        try{
            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            // 代码泄露可能会导致 SecretId 和 SecretKey 泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考，建议采用更安全的方式来使用密钥，请参见：https://cloud.tencent.com/document/product/1278/85305
            // 密钥可前往官网控制台 https://console.cloud.tencent.com/cam/capi 进行获取
            Credential cred = new Credential(this.vodConstantProperties.getSecretId(), this.vodConstantProperties.getSecretKey());
            // 实例化要请求产品的client对象,clientProfile是可选的
            VodClient client = new VodClient(cred, this.vodConstantProperties.getRegion());
            // 实例化一个请求对象,每个接口都会对应一个request对象
            DeleteMediaRequest req = new DeleteMediaRequest();
            req.setFileId(mediaFileId);
//            req.setSubAppId(this.vodConstantProperties.getAppId());

            // 返回的resp是一个DeleteMediaResponse的实例，与请求对象对应
            DeleteMediaResponse resp = client.DeleteMedia(req);
            // 输出json格式的字符串回包
//            System.out.println(DeleteMediaResponse.toJsonString(resp));
        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
        }
    }

    @Override
    public TrackMediaInfoVo getMediaInfo(String mediaFileId) {
        try {
            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            // 代码泄露可能会导致 SecretId 和 SecretKey 泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考，建议采用更安全的方式来使用密钥，请参见：https://cloud.tencent.com/document/product/1278/85305
            // 密钥可前往官网控制台 https://console.cloud.tencent.com/cam/capi 进行获取
            Credential cred = new Credential(this.vodConstantProperties.getSecretId(), this.vodConstantProperties.getSecretKey());
            // 实例化要请求产品的client对象
            VodClient client = new VodClient(cred, this.vodConstantProperties.getRegion());
            // 实例化一个请求对象,每个接口都会对应一个request对象
            DescribeMediaInfosRequest req = new DescribeMediaInfosRequest();
            req.setFileIds(new String[]{mediaFileId});

            // 返回的resp是一个DescribeMediaInfosResponse的实例，与请求对象对应
            DescribeMediaInfosResponse resp = client.DescribeMediaInfos(req);
            // 获取媒体信息列表，这里咱们只验证了一个文件
            MediaInfo[] mediaInfoSet = resp.getMediaInfoSet();
            if (mediaInfoSet != null && mediaInfoSet.length > 0){
                MediaInfo mediaInfo = mediaInfoSet[0];
                // 组装响应对象
                TrackMediaInfoVo mediaInfoVo = new TrackMediaInfoVo();
                mediaInfoVo.setSize(mediaInfo.getMetaData().getSize());
                mediaInfoVo.setDuration(mediaInfo.getMetaData().getDuration());
                mediaInfoVo.setMediaUrl(mediaInfo.getBasicInfo().getMediaUrl());
                mediaInfoVo.setType(mediaInfo.getBasicInfo().getType());
                return mediaInfoVo;
            }
            return null;
        } catch (TencentCloudSDKException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getPlayToken(String mediaFileId) {
        Integer AppId = vodConstantProperties.getAppId();
        String FileId = mediaFileId;
        String AudioVideoType = "Original";
        Integer RawAdaptiveDefinition = 10;
        Integer ImageSpriteDefinition = 10;
        Integer CurrentTime = Math.toIntExact(new Date().getTime() / 1000);//播放器签名的派发时间为
        Integer PsignExpire = CurrentTime + 60 * 60 * 2;//播放器签名的过期时间，根据录制最长时间设置，如：两小时
        String UrlTimeExpire = String.valueOf(PsignExpire);//防盗链的过期时间
        String PlayKey = vodConstantProperties.getPlayKey();//播放密钥
        HashMap<String, Object> urlAccessInfo = new HashMap<String, Object>();
        urlAccessInfo.put("t", UrlTimeExpire);
        HashMap<String, Object> contentInfo = new HashMap<String, Object>();
        contentInfo.put("audioVideoType", AudioVideoType);
        contentInfo.put("rawAdaptiveDefinition", RawAdaptiveDefinition);
        contentInfo.put("imageSpriteDefinition", ImageSpriteDefinition);

        Algorithm algorithm = Algorithm.HMAC256(PlayKey);
        String token = JWT.create().withClaim("appId", AppId).withClaim("fileId", FileId)
                .withClaim("contentInfo", contentInfo)
                .withClaim("currentTimeStamp", CurrentTime).withClaim("expireTimeStamp", PsignExpire)
                .withClaim("urlAccessInfo", urlAccessInfo).sign(algorithm);
        System.out.println("token:" + token);
        return token;

    }
}
