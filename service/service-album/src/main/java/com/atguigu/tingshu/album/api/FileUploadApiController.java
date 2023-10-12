package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.config.MinioConstantProperties;
import com.atguigu.tingshu.common.result.Result;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Tag(name = "上传管理接口")
@RestController
@RequestMapping("api/album")
public class FileUploadApiController {

    @Autowired
    private MinioConstantProperties minioConstantProperties;
    @Autowired
    MinioClient minioClient;
    @PostMapping("fileUpload")
    public Result<String> fileUpload(MultipartFile file) throws Exception {
        String url;
        String id= UUID.randomUUID().toString();
        InputStream inputStream = file.getInputStream();
        PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                .bucket("tingshu")
                .stream(inputStream, inputStream.available(), -1)
                .object("test/" + id+".jpg")
                .build();
        minioClient.putObject(putObjectArgs);
        url="http://192.168.160.130:9001/tingshu/"+"test/"+id+".jpg";
        return Result.ok(url);
    }

}
