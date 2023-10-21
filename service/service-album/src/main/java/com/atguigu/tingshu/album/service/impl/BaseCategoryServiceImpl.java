package com.atguigu.tingshu.album.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.album.mapper.*;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.model.album.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class BaseCategoryServiceImpl extends ServiceImpl<BaseCategory1Mapper, BaseCategory1> implements BaseCategoryService {

	@Autowired
	private BaseCategory1Mapper baseCategory1Mapper;

	@Autowired
	private BaseCategory2Mapper baseCategory2Mapper;

	@Autowired
	private BaseCategory3Mapper baseCategory3Mapper;
	@Autowired
	private BaseCategoryViewMapper baseCategoryViewMapper;
	@Autowired
	private BaseAttributeMapper baseAttributeMapper;
	@Override
	public List<JSONObject> getBaseCategoryList() {
		List<JSONObject> result = new ArrayList<>();
		List<BaseCategory1> baseCategory1s = baseCategory1Mapper.selectList(null);
		for(int i=0;i<baseCategory1s.size();i++){
			List<JSONObject> list2 = new ArrayList<>();
			JSONObject current1 = new JSONObject();
			List<BaseCategory2> baseCategory2s = baseCategory2Mapper.selectList(
					new QueryWrapper<BaseCategory2>().eq("category1_id",baseCategory1s.get(i).getId()));

			for(int j=0;j<baseCategory2s.size();j++){
				JSONObject current2 = new JSONObject();
				BaseCategory2 baseCategory2 = baseCategory2s.get(j);
				List<BaseCategory3> baseCategory3s = baseCategory3Mapper.selectList(
						new QueryWrapper<BaseCategory3>().eq("category2_id",baseCategory2s.get(j).getId())
				);
				Stream<JSONObject> category3 = baseCategory3s.stream().map((t) -> {
					JSONObject current3 = new JSONObject();
					current3.put("categoryName", t.getName());
					current3.put("categoryId", t.getId());
					return current3;
				});
				current2.put("categoryChild",category3.toList());
				current2.put("categoryName",baseCategory2.getName());
				current2.put("categoryId",baseCategory2.getId());
				list2.add(current2);
			}

			current1.put("categoryChild",list2);
			current1.put("categoryName",baseCategory1s.get(i).getName());
			current1.put("categoryId",baseCategory1s.get(i).getId());
			result.add(current1);
		}

		return result;
	}

	@Override
	public List<BaseAttribute> findAttributeByCategory1Id(Long category1Id) {
		//	调用mapper层方法
		return baseAttributeMapper.selectBaseAttributeList(category1Id);
	}

	@Override
	public BaseCategoryView getCategoryViewByCategory3Id(Long category3Id) {
		return baseCategoryViewMapper.selectById(category3Id);
	}

	@Override
	public List<BaseCategory3> findTopBaseCategory3ByCategory1Id(Long category1Id) {
		//	select * from base_category3 where base_category3.category2_id in (101,102,103) and is_top = 1 limit 7;
		//	先根据一级分类Id 找到二级分类集合
		LambdaQueryWrapper<BaseCategory2> baseCategory2LambdaQueryWrapper = new LambdaQueryWrapper<>();
		baseCategory2LambdaQueryWrapper.eq(BaseCategory2::getCategory1Id,category1Id);
		List<BaseCategory2> baseCategory2List = baseCategory2Mapper.selectList(baseCategory2LambdaQueryWrapper);
		List<Long> category2IdList = baseCategory2List.stream().map(BaseCategory2::getId).collect(Collectors.toList());
		//	查询置顶消息，每页显示7条数据；
		LambdaQueryWrapper<BaseCategory3> wrapper = new LambdaQueryWrapper<>();
		wrapper.in(BaseCategory3::getCategory2Id,category2IdList).eq(BaseCategory3::getIsTop,1).last("limit 7");
		return baseCategory3Mapper.selectList(wrapper);
	}

	@Override
	public JSONObject getAllCategoryList(Long category1Id) {
		BaseCategory1 baseCategory1 = baseCategory1Mapper.selectById(category1Id);
		// 声明一级分类对象
		JSONObject category1 = new JSONObject();
		category1.put("categoryId", category1Id);
		category1.put("categoryName", baseCategory1.getName());

		//获取全部分类信息
		List<BaseCategoryView> baseCategoryViewList = baseCategoryViewMapper.selectList(new LambdaQueryWrapper<BaseCategoryView>().eq(BaseCategoryView::getCategory1Id, category1Id));

		//根据二级分类ID分组转换数据
		Map<Long, List<BaseCategoryView>> category2Map = baseCategoryViewList.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));
		List<JSONObject> category2Child = new ArrayList<>();
		for(Map.Entry<Long, List<BaseCategoryView>> entry2 : category2Map.entrySet()) {
			//二级分类ID
			Long category2Id = entry2.getKey();
			//二级分类对应的全部数据（三级数据）
			List<BaseCategoryView> category3List = entry2.getValue();

			// 声明二级分类对象
			JSONObject category2 = new JSONObject();
			category2.put("categoryId", category2Id);
			category2.put("categoryName", category3List.get(0).getCategory2Name());

			// 循环三级分类数据
			List<JSONObject> category3Child = new ArrayList<>();
			category3List.stream().forEach(category3View -> {
				JSONObject category3 = new JSONObject();
				category3.put("categoryId", category3View.getCategory3Id());
				category3.put("categoryName", category3View.getCategory3Name());
				category3Child.add(category3);
			});
			category2Child.add(category2);
			// 将三级数据放入二级里面
			category2.put("categoryChild", category3Child);
		}
		// 将二级数据放入一级里面
		category1.put("categoryChild", category2Child);
		return category1;
	}

	@Override
	public List<BaseCategory1> findAllCategory1() {
		return baseCategory1Mapper.selectList(new LambdaQueryWrapper<BaseCategory1>().orderByAsc(BaseCategory1::getOrderNum));
	}
}
