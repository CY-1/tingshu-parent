package com.atguigu.tingshu.album.controller;

import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.vo.album.TrackStatVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "声音管理")
@RestController
@RequestMapping("admin/album/trackInfo")
@SuppressWarnings({"unchecked", "rawtypes"})
public class TrackInfoController {

	@Autowired
	private TrackInfoService trackInfoService;

}

