package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.login.GuiGuLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumTrackListVo;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.atguigu.tingshu.vo.album.TrackStatVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "声音管理")
@RestController
@RequestMapping("api/album/trackInfo")
@SuppressWarnings({"unchecked", "rawtypes"})
public class TrackInfoApiController {

	@Autowired
	private TrackInfoService trackInfoService;

	@Autowired
	private VodService vodService;
	/**
	 * 上传声音
	 * @param file
	 * @return
	 */
	@Operation(summary = "上传声音")
	@PostMapping("uploadTrack")
	public Result<Map<String,Object>> uploadTrack(MultipartFile file) {
		//	调用服务层方法
		Map<String,Object> map = vodService.uploadTrack(file);
		return Result.ok(map);
	}
	@Operation(summary = "新增声音")
	@PostMapping("saveTrackInfo")
	public Result saveTrackInfo(@RequestBody @Validated TrackInfoVo trackInfoVo) {
		//	调用服务层方法
		trackInfoService.saveTrackInfo(trackInfoVo);
		return Result.ok();
	}
	/**
	 * 查看声音专辑列表
	 * @param page
	 * @param limit
	 * @param trackInfoQuery
	 * @return
	 */

	@Operation(summary = "获取当前用户声音分页列表")
	@PostMapping("findUserTrackPage/{page}/{limit}")
	public Result<IPage<TrackListVo>> findUserTrackPage(@Parameter(name = "page",description = "当前页面",required = true)
														@PathVariable Long page,
														@Parameter(name = "limit",description = "每页记录数",required = true)
														@PathVariable Long limit,
														@Parameter(name = "trackInfoQuery",description = "查询对象",required = false)
														@RequestBody TrackInfoQuery trackInfoQuery){
		//	设置当前用户Id
		trackInfoQuery.setUserId(AuthContextHolder.getUserId()==null?1:AuthContextHolder.getUserId());
		//	创建对象
		Page<TrackListVo> trackListVoPage = new Page<>(page,limit);
		IPage<TrackListVo> trackListVoIPage = trackInfoService.findUserTrackPage(trackListVoPage,trackInfoQuery);
		//	返回数据
		return Result.ok(trackListVoIPage);
	}
	@Operation(summary = "获取声音信息")
	@GetMapping("getTrackInfo/{id}")
	public Result<TrackInfo> getTrackInfo(@PathVariable Long id) {
		//	调用服务层方法
		TrackInfo trackInfo = trackInfoService.getById(id);
		return Result.ok(trackInfo);
	}
	/**
	 * 保存修改声音数据
	 * @param id
	 * @param trackInfoVo
	 * @return
	 */
	@Operation(summary = "修改声音")
	@PutMapping("updateTrackInfo/{id}")
	public Result updateById(@PathVariable Long id, @RequestBody @Validated TrackInfoVo trackInfoVo) {
		//	调用服务层方法
		trackInfoService.updateTrackInfo(id, trackInfoVo);
		return Result.ok();
	}
	/**
	 * 删除声音
	 * @param id
	 * @return
	 */
	@Operation(summary = "删除声音信息")
	@DeleteMapping("removeTrackInfo/{id}")
	public Result removeTrackInfo(@PathVariable Long id) {
		//	调用服务层方法
		trackInfoService.removeTrackInfo(id);
		return Result.ok();
	}
	/**
	 * 根据专辑Id获取声音列表
	 * @param albumId
	 * @param page
	 * @param limit
	 * @return
	 */
	@GuiGuLogin(required = false)
	@Operation(summary = "获取专辑声音分页列表")
	@GetMapping("findAlbumTrackPage/{albumId}/{page}/{limit}")
	public Result<IPage<AlbumTrackListVo>> findAlbumTrackPage(
			@Parameter(name = "albumId", description = "专辑id", required = true)
			@PathVariable Long albumId,

			@Parameter(name = "page", description = "当前页码", required = true)
			@PathVariable Long page,

			@Parameter(name = "limit", description = "每页记录数", required = true)
			@PathVariable Long limit) {
		//	获取用户Id
		Long userId = AuthContextHolder.getUserId()==null?1:AuthContextHolder.getUserId();
		//	构建分页对象
		Page<AlbumTrackListVo> pageParam = new Page<>(page, limit);
		//	调用服务层方法
		IPage<AlbumTrackListVo> pageModel = trackInfoService.findAlbumTrackPage(pageParam, albumId, userId);
		//	返回数据
		return Result.ok(pageModel);
	}

	/**
	 * 获取声音统计接口
	 * @param trackId
	 * @return
	 */
	@Operation(summary = "获取声音统计信息")
	@GetMapping("getTrackStatVo/{trackId}")
	public Result<TrackStatVo> getTrackStatVo(@PathVariable Long trackId) {
		//	调用服务层方法
		TrackStatVo trackStatVo = trackInfoService.getTrackStatVoByTrackId(trackId);
		//	返回对象
		return Result.ok(trackStatVo);
	}
	/**
	 * 播放声音
	 * @param trackId
	 * @return
	 */
	@GuiGuLogin(required = false)
	@Operation(summary = "获取声音播放凭证")
	@GetMapping("getPlayToken/{trackId}")
	public Result<Map<String,Object>> getPlayToken(@PathVariable Long trackId) {
		//	获取用户Id
		Long userId = AuthContextHolder.getUserId();
		//	获取播放凭证
		Map<String,Object> map = trackInfoService.getPlayToken(userId, trackId);
		//	返回播放凭证
		return Result.ok(map);
	}
}

