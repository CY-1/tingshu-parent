package com.atguigu.tingshu.album.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.album.mapper.*;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.model.album.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
}
