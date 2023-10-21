package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.common.login.GuiGuLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@Tag(name = "专辑管理")
@RestController
@RequestMapping("api/album/albumInfo")
@SuppressWarnings({"unchecked", "rawtypes"})
public class AlbumInfoApiController {

	@Autowired
	private AlbumInfoService albumInfoService;
	@Autowired
	private BaseCategoryService baseCategoryService;

	/**
	 * 新增专辑方法
	 * @param albumInfoVo
	 * @return
	 */
	@Operation(summary = "新增专辑")
	@PostMapping("saveAlbumInfo")
	public Result save(@RequestBody @Validated AlbumInfoVo albumInfoVo) {
		//	调用服务层保存方法

		albumInfoService.saveAlbumInfo(albumInfoVo, AuthContextHolder.getUserId()==null?1:AuthContextHolder.getUserId());
		return Result.ok();
	}
	/**
	 * 根据条件查询专辑列表
	 * @param page
	 * @param limit
	 * @param albumInfoQuery
	 * @return
	 */
//	@GuiGuLogin
	@Operation(summary = "获取当前用户专辑分页列表")
	@PostMapping("findUserAlbumPage/{page}/{limit}")
	public Result findUserAlbumPage(@Parameter(name = "page",description = "当前页码",required = true)
									@PathVariable Long page,
									@Parameter(name = "limit", description = "每页记录数", required = true)
									@PathVariable Long limit,
									@Parameter(name = "albumInfoQuery", description = "查询对象", required = false)
									@RequestBody AlbumInfoQuery albumInfoQuery
	){
		//	获取数据：
		albumInfoQuery.setUserId(AuthContextHolder.getUserId());
		Page<AlbumListVo> albumInfoPage = new Page<>(page,limit);
		//	调用服务层方法
		IPage<AlbumListVo> iPage = this.albumInfoService.findUserAlbumPage(albumInfoPage,albumInfoQuery);
		//	返回数据集
		return Result.ok(iPage);
	}
	@Operation(summary = "删除专辑信息")
	@DeleteMapping("removeAlbumInfo/{id}")
	public Result removeAlbumInfoById(@PathVariable Long id) {
		albumInfoService.removeAlbumInfoById(id);
		return Result.ok();
	}
	@Operation(summary = "获取专辑信息")
	@GetMapping("getAlbumInfo/{id}")
	public Result<AlbumInfo> getAlbumInfoById(@PathVariable Long id) {
		//	调用服务层方法
		AlbumInfo albumInfo = albumInfoService.getAlbumInfoById(id);
		return Result.ok(albumInfo);
	}
	@Operation(summary = "修改专辑")
	@PutMapping("/updateAlbumInfo/{id}")
	public Result updateById(@PathVariable Long id,@RequestBody @Validated AlbumInfoVo albumInfoVo){
		//	调用服务层方法
		albumInfoService.updateAlbumInfo(id,albumInfoVo);
		return Result.ok();
	}

	@Operation(summary = "获取当前用户全部专辑列表")
	@GetMapping("findUserAllAlbumList")
	public Result findUserAllAlbumList() {
		//	调用服务层方法
		List<AlbumInfo> list = albumInfoService.findUserAllAlbumList(AuthContextHolder.getUserId());
		return Result.ok(list);
	}
	/**
	 * 根据专辑Id 获取到专辑属性列表
	 * @param albumId
	 * @return
	 */
	@Operation(summary = "获取专辑属性值列表")
	@GetMapping("findAlbumAttributeValue/{albumId}")
	public Result<List<AlbumAttributeValue>> findAlbumAttributeValue(@PathVariable Long albumId) {
		//	获取到专辑属性集合
		List<AlbumAttributeValue> albumAttributeValueList = albumInfoService.findAlbumAttributeValueByAlbumId(albumId);
		return Result.ok(albumAttributeValueList);
	}
	/**
	 * 根据三级分类Id 获取到分类信息
	 * @param category3Id
	 * @return
	 */
	@Operation(summary = "通过三级分类id查询分类信息")
	@GetMapping("getCategoryView/{category3Id}")
	public Result<BaseCategoryView> getCategoryView(@PathVariable Long category3Id){
		// 调用服务层方法
		BaseCategoryView baseCategoryView = baseCategoryService.getCategoryViewByCategory3Id(category3Id);
		return Result.ok(baseCategoryView);
	}
	/**
	 * 根据专辑Id 获取到统计信息
	 * @param albumId
	 * @return
	 */
	@Operation(summary = "获取到专辑统计信息")
	@GetMapping("/getAlbumStatVo/{albumId}")
	public Result getAlbumStatVo(@PathVariable Long albumId){
		//	获取服务层方法
		AlbumStatVo albumStatVo = this.albumInfoService.getAlbumStatVoByAlbumId(albumId);
		return Result.ok(albumStatVo);
	}
}

