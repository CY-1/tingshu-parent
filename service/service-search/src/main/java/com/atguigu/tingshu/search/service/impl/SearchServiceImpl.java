package com.atguigu.tingshu.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggester;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.album.client.AlbumInfoFeignClient;
import com.atguigu.tingshu.album.client.CategoryFeignClient;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.PinYinUtils;
import com.atguigu.tingshu.model.album.*;

import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.model.search.AttributeValueIndex;
import com.atguigu.tingshu.model.search.SuggestIndex;
import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.search.repository.AlbumIndexRepository;
import com.atguigu.tingshu.search.repository.SuggestIndexRepository;
import com.atguigu.tingshu.search.service.SearchService;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.vo.search.AlbumInfoIndexVo;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.suggest.Completion;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class SearchServiceImpl implements SearchService {

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private AlbumIndexRepository albumIndexRepository;
    @Autowired
    private AlbumInfoFeignClient albumInfoFeignClient;
    @Autowired
    private CategoryFeignClient categoryFeignClient;
    @Autowired
    private UserInfoFeignClient userInfoFeignClient;
    @Autowired
    private ElasticsearchClient elasticsearchClient;
    @Autowired
    private SuggestIndexRepository suggestIndexRepository;
    @Override
    public void upperAlbum(Long albumId) {
        //  获取专辑信息

        Result<AlbumInfo> albumInfoResult = albumInfoFeignClient.getAlbumInfo(albumId);
        AlbumInfo albumInfo = albumInfoResult.getData();
        //注意import别到错包
        Assert.notNull(albumInfo,"专辑为空");
        //  获取专辑属性信息
        Result<List<AlbumAttributeValue>> albumAttributeValueResult = albumInfoFeignClient.findAlbumAttributeValue(albumId);
        List<AlbumAttributeValue> albumAttributeValueList = albumAttributeValueResult.getData();
        Assert.notNull(albumAttributeValueList,"专辑属性为空");
        //  根据三级分类Id 获取到分类数据
        Result<BaseCategoryView> baseCategoryViewResult = categoryFeignClient.getCategoryView(albumInfo.getCategory3Id());
        BaseCategoryView baseCategoryView = baseCategoryViewResult.getData();
        Assert.notNull(baseCategoryView,"分类为空");
        //  根据用户Id 获取到用户信息
        Result<UserInfoVo> userInfoVoResult = userInfoFeignClient.getUserInfoVo(albumInfo.getUserId());
        UserInfoVo userInfoVo = userInfoVoResult.getData();
        Assert.notNull(userInfoVo,"用户信息为空");

        //  创建索引库对象
        AlbumInfoIndex albumInfoIndex = new AlbumInfoIndex();
        BeanUtils.copyProperties(albumInfo,albumInfoIndex);


        //  赋值属性值信息
        if (!CollectionUtils.isEmpty(albumAttributeValueList)){
            List<AttributeValueIndex> attributeValueIndexList = albumAttributeValueList.stream().map(albumAttributeValue -> {
                AttributeValueIndex attributeValueIndex = new AttributeValueIndex();
                BeanUtils.copyProperties(albumAttributeValue, attributeValueIndex);
                return attributeValueIndex;
            }).collect(Collectors.toList());
            //  保存数据
            albumInfoIndex.setAttributeValueIndexList(attributeValueIndexList);
        }
        //  赋值分类数据
        albumInfoIndex.setCategory1Id(baseCategoryView.getCategory1Id());
        albumInfoIndex.setCategory2Id(baseCategoryView.getCategory2Id());
        albumInfoIndex.setCategory3Id(baseCategoryView.getCategory3Id());
        //  赋值主播名称
        albumInfoIndex.setAnnouncerName(userInfoVo.getNickname());

        //更新统计量与得分，默认随机，方便测试
        int num1 = new Random().nextInt(1000);
        int num2 = new Random().nextInt(100);
        int num3 = new Random().nextInt(50);
        int num4 = new Random().nextInt(300);
        albumInfoIndex.setPlayStatNum(num1);
        albumInfoIndex.setSubscribeStatNum(num2);
        albumInfoIndex.setBuyStatNum(num3);
        albumInfoIndex.setCommentStatNum(num4);
        double hotScore = num1*0.2 + num2*0.3 + num3*0.4 + num4*0.1;
        //  设置热度排名
        albumInfoIndex.setHotScore(hotScore);
        //  保存商品上架信息
        albumIndexRepository.save(albumInfoIndex);
        //  更新关键字智能提示库
//  专辑标题
        SuggestIndex titleSuggestIndex = new SuggestIndex();
        titleSuggestIndex.setId(UUID.randomUUID().toString().replaceAll("-", ""));
        titleSuggestIndex.setTitle(albumInfoIndex.getAlbumTitle());
        titleSuggestIndex.setKeyword(new Completion(new String[]{albumInfoIndex.getAlbumTitle()}));
        titleSuggestIndex.setKeywordPinyin(new Completion(new String[]{PinYinUtils.toHanyuPinyin(albumInfoIndex.getAlbumTitle())}));
        titleSuggestIndex.setKeywordSequence(new Completion(new String[]{PinYinUtils.getFirstLetter(albumInfoIndex.getAlbumTitle())}));
        suggestIndexRepository.save(titleSuggestIndex);

// 专辑主播
        SuggestIndex announcerSuggestIndex = new SuggestIndex();
        announcerSuggestIndex.setId(UUID.randomUUID().toString().replaceAll("-", ""));
        announcerSuggestIndex.setTitle(albumInfoIndex.getAnnouncerName());
        announcerSuggestIndex.setKeyword(new Completion(new String[]{albumInfoIndex.getAnnouncerName()}));
        announcerSuggestIndex.setKeywordPinyin(new Completion(new String[]{PinYinUtils.toHanyuPinyin(albumInfoIndex.getAnnouncerName())}));
        announcerSuggestIndex.setKeywordSequence(new Completion(new String[]{PinYinUtils.getFirstLetter(albumInfoIndex.getAnnouncerName())}));
        suggestIndexRepository.save(announcerSuggestIndex);

    }

    @Override
    public void lowerAlbum(Long albumId) {
        albumIndexRepository.deleteById(albumId);
    }

    @Override
    public AlbumSearchResponseVo search(AlbumIndexQuery albumIndexQuery) {
        // 构建dsl语句
        SearchRequest request = this.buildQueryDsl(albumIndexQuery);
        //  调用查询方法
        SearchResponse<AlbumInfoIndex> response = null;
        try {
            response = elasticsearchClient.search(request, AlbumInfoIndex.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //  得到返回的结果集
        AlbumSearchResponseVo responseVO = this.parseSearchResult(response);
        responseVO.setPageSize(albumIndexQuery.getPageSize());
        responseVO.setPageNo(albumIndexQuery.getPageNo());
        // 获取总页数
        long totalPages = (responseVO.getTotal() + albumIndexQuery.getPageSize() - 1) / albumIndexQuery.getPageSize();
        responseVO.setTotalPages(totalPages);
        return responseVO;


    }

    @Override
    public List<Map<String, Object>> channel(Long category1Id) throws IOException {
        //  根据一级分类Id获取到置顶数据集合
        Result<List<BaseCategory3>> baseCategory3ListResult = categoryFeignClient.findTopBaseCategory3(category1Id);
        Assert.notNull(baseCategory3ListResult,"分类结果集不能为空");
        List<BaseCategory3> baseCategory3List = baseCategory3ListResult.getData();
        Assert.notNull(baseCategory3List,"分类集合不能为空");

        //  建立对应关系 key=三级分类Id value=三级分类对象
        Map<Long, BaseCategory3> category3IdToMap = baseCategory3List.stream().collect(Collectors.toMap(BaseCategory3::getId, baseCategory3 -> baseCategory3));
        //  获取置顶的三级分类Id 列表
        List<Long> idList = baseCategory3List.stream().map(BaseCategory3::getId).collect(Collectors.toList());

        List<FieldValue> valueList = idList.stream().map(id -> FieldValue.of(id)).collect(Collectors.toList());

        //  调用查询方法.
        SearchResponse<AlbumInfoIndex> response = elasticsearchClient.search(s -> s
                        .index("albuminfo")
                        .size(0)
                        .query(q -> q.terms(t -> t.field("category3Id").terms(new TermsQueryField.Builder().value(valueList).build())))
                        .aggregations("groupByCategory3IdAgg", a -> a
                                .terms(t -> t.field("category3Id")
                                )
                                .aggregations("topTenHotScoreAgg", a1 -> a1
                                        .topHits(t -> t
                                                .size(6)
                                                .sort(sort -> sort.field(f -> f.field("hotScore").order(SortOrder.Desc)))))
                        )
                , AlbumInfoIndex.class);

        List<Map<String, Object>> result = new ArrayList<>();
        //  从聚合中获取数据
        Aggregate groupByCategory3IdAgg = response.aggregations().get("groupByCategory3IdAgg");
        groupByCategory3IdAgg.lterms().buckets().array().forEach(item ->{
            List<AlbumInfoIndex> albumInfoIndexList = new ArrayList<>();
            Long category3Id = item.key();
            Aggregate topTenHotScoreAgg = item.aggregations().get("topTenHotScoreAgg");
            topTenHotScoreAgg.topHits().hits().hits().forEach(hit -> {
                AlbumInfoIndex albumInfoIndex = JSONObject.parseObject(hit.source().toString(), AlbumInfoIndex.class);
                albumInfoIndexList.add(albumInfoIndex);
            });
            Map<String, Object> map = new HashMap<>();
            map.put("baseCategory3", category3IdToMap.get(category3Id));
            map.put("list", albumInfoIndexList);

            result.add(map);
        });

        return result;
    }

    @SneakyThrows
    @Override
    public List<String> completeSuggest(String keyword) {
        //  创建Suggester 对象
        Suggester suggester = new Suggester.Builder()
                .suggesters("suggestionKeyword", s ->
                        s.prefix(keyword)
                                .completion(c -> c.field("keyword")
                                        .size(10)
                                        .skipDuplicates(true)))
                .suggesters("suggestionKeywordSequence", s ->
                        s.prefix(keyword)
                                .completion(c -> c.field("keywordSequence")
                                        .size(10)
                                        .skipDuplicates(true)))
                .suggesters("suggestionKeywordPinyin", s ->
                        s.prefix(keyword)
                                .completion(c -> c.field("keywordPinyin")
                                        .size(10)
                                        .skipDuplicates(true))).build();
        //  打印对象
        System.out.println(suggester.toString());
        SearchResponse<SuggestIndex> response = elasticsearchClient.search(s ->
                        s.index("suggestinfo")
                                .suggest(suggester),
                SuggestIndex.class);

        //  处理关键字搜索结构
        HashSet<String> titleSet = new HashSet<>();
        titleSet.addAll(this.parseSearchResult(response, "suggestionKeyword"));
        titleSet.addAll(this.parseSearchResult(response, "suggestionKeywordPinyin"));
        titleSet.addAll(this.parseSearchResult(response, "suggestionKeywordSequence"));

        //  判断
        if (titleSet.size() < 10) {
            SearchResponse<SuggestIndex> responseSuggest = elasticsearchClient.search(s ->
                    s.index("suggestinfo")
                            .size(10)
                            .query(q -> q.match(m -> m.field("title").query(keyword))), SuggestIndex.class);
            List<Hit<SuggestIndex>> hits = responseSuggest.hits().hits();
            //  循环遍历
            for (Hit<SuggestIndex> hit : hits) {
                titleSet.add(hit.source().getTitle());
                //  计算智能推荐总个数最大10个
                int total = titleSet.size();
                if (total>=10) break;
            }
        }
        //  返回这个集合
        return new ArrayList<>(titleSet);
    }

    @SneakyThrows
    @Override
    public void updateLatelyAlbumRanking() {
        //  排行榜，按分类维度统计, 先获取分类数据
        Result<List<BaseCategory1>> baseCategory1Result = categoryFeignClient.findAllCategory1();
        Assert.notNull(baseCategory1Result,"对象不能为空");
        List<BaseCategory1> baseCategory1List = baseCategory1Result.getData();
        if (!CollectionUtils.isEmpty(baseCategory1List)){
            //  循环遍历
            for (BaseCategory1 baseCategory1 : baseCategory1List) {
                // 统计维度：热度:hotScore、播放量:playStatNum、订阅量:subscribeStatNum、购买量:buyStatNum、评论数:albumCommentStatNum
                String[] rankingDimensionArray = new String[]{"hotScore", "playStatNum", "subscribeStatNum", "buyStatNum", "commentStatNum"};
                for (String rankingDimension : rankingDimensionArray) {
                    SearchResponse<AlbumInfoIndex> response = elasticsearchClient.search(s -> s
                            .index("albuminfo")
                            //课件上没有 手动加上的
                            .query(ss->ss.term(tt->tt.field("category1Id").value(baseCategory1.getId())))
                            .size(10)
                            .sort(t -> t.field(f -> f.field(rankingDimension).order(SortOrder.Desc))), AlbumInfoIndex.class);

                    //  解析查询列表
                    List<Hit<AlbumInfoIndex>> albumInfoIndexHitList = response.hits().hits();
                    List<AlbumInfoIndex> albumInfoIndexList = albumInfoIndexHitList.stream().map(hit -> hit.source()).collect(Collectors.toList());
                    //  将排行榜数据更新到缓存中
                    this.redisTemplate.boundHashOps(RedisConstant.RANKING_KEY_PREFIX+baseCategory1.getId()).put(rankingDimension,albumInfoIndexList);
                }
            }
        }
    }

    @Override
    public List<AlbumInfoIndexVo> findRankingList(Long category1Id, String dimension) {
        if(dimension.equals("albumCommentStatNum")){
            //前后端不一致的地方
            dimension="commentStatNum";
        }
        return (List<AlbumInfoIndexVo>) redisTemplate.boundHashOps(RedisConstant.RANKING_KEY_PREFIX + category1Id).get(dimension);

    }

    /**
     * 处理聚合结果集
     * @param response
     * @param suggestName
     * @return
     */
    private List<String> parseSearchResult(SearchResponse<SuggestIndex> response, String suggestName) {
        //  创建集合
        List<String> suggestList = new ArrayList<>();
        Map<String, List<Suggestion<SuggestIndex>>> groupBySuggestionListAggMap = response.suggest();
        groupBySuggestionListAggMap.get(suggestName).forEach(item -> {
            CompletionSuggest<SuggestIndex> completionSuggest =  item.completion();
            completionSuggest.options().forEach(it -> {
                SuggestIndex suggestIndex = it.source();
                suggestList.add(suggestIndex.getTitle());
            });
        });
        //  返回集合列表
        return suggestList;
    }
    private SearchRequest buildQueryDsl(AlbumIndexQuery albumIndexQuery) {
        // 1. 构造查询条件
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // 1.1. 查询关键字
        String keyword = albumIndexQuery.getKeyword();
        if (!StringUtils.isEmpty(keyword)) {
            BoolQuery.Builder keywordBoolQuery = new BoolQuery.Builder();
            keywordBoolQuery.should(s -> s.match(m -> m.field("albumTitle").query(keyword)));
            keywordBoolQuery.should(s -> s.match(m -> m.field("albumIntro").query(keyword)));
            keywordBoolQuery.should(s -> s.match(m -> m.field("announcerName").query(keyword)));
            boolQuery.must(keywordBoolQuery.build()._toQuery());

        }

        // 1.2.  构建分类的过滤
        Long category1Id = albumIndexQuery.getCategory1Id();
        if (null != category1Id) {
            boolQuery.filter(f -> f.term(t -> t.field("category1Id").value(category1Id)));
        }
        Long category2Id = albumIndexQuery.getCategory2Id();
        if (null != category2Id) {
            boolQuery.filter(f -> f.term(t -> t.field("category2Id").value(category2Id)));
        }
        Long category3Id = albumIndexQuery.getCategory3Id();
        if (null != category3Id) {
            boolQuery.filter(f -> f.term(t -> t.field("category3Id").value(category3Id)));
        }

        // 1.3.  构建分类属性嵌套过滤
        List<String> attributeList = albumIndexQuery.getAttributeList();
        if (!CollectionUtils.isEmpty(attributeList)) {
            for (String attribute : attributeList) {
                // 以：进行分割，分割后应该是2个元素，属性id:属性值id (以-分割的字符串)
                String[] split = StringUtils.split(attribute, ":");
                if (split != null && split.length == 2) {
                    Query nestedQuery = NestedQuery.of(n -> n
                            .path("attributeValueIndexList")
                            .query(q -> q.bool(b -> b
                                            .must(m -> m.term(t -> t.field("attributeValueIndexList.attributeId").value(split[0])))
                                            .must(m -> m.term(t -> t.field("attributeValueIndexList.valueId").value(split[1])))
                                    )
                            )
                    )._toQuery();
                    boolQuery.filter(nestedQuery);
                }
            }
        }

        //1.4. 查询条件
        Query query = boolQuery.build()._toQuery();

        // 2.1 构建分页
        Integer pageNo = albumIndexQuery.getPageNo();
        Integer pageSize = albumIndexQuery.getPageSize();

        // 3.1. 构建排序
        String order = albumIndexQuery.getOrder();
        String orderField = "hotScore";
        String sort = "desc";
        String field = "hotScore";
        if (!StringUtils.isEmpty(order)) {
            String[] split = StringUtils.split(order, ":");
            if (split != null && split.length == 2) {

                switch (split[0]) {
                    case "1":
                        field = "hotScore";
                        break;
                    case "2":
                        field = "playStatNum";
                        break;
                    case "3":
                        field = "createTime";
                        break;
                }
                sort = split[1];
            }
        }

        //拼接查询条件
        SearchRequest.Builder builder = new SearchRequest.Builder()
                //去哪个索引里搜索
                .index("albuminfo")
                //构建分页
                .from((pageNo - 1) * pageSize)
                .size(pageSize)
                //构建查询
                .query(query)
                .highlight(h -> h
                        .fields("albumTitle", f -> f
                                .preTags("<font color='red'>")
                                .postTags("</font>")
                        )
                )
                .source(s -> s.filter(f -> f.excludes("attributeValueIndexList", "hotScore")));
        //关键字搜索按照ES默认排序，这样显示更准确
        if (StringUtils.isEmpty(keyword)) {
            String finalSort = sort;

            String finalField = field;
            builder.sort(s -> s.field(f -> f.field(finalField).order("asc".equals(finalSort) ? SortOrder.Asc : SortOrder.Desc)));
        }
        SearchRequest request = builder.build();
        System.out.println(request.toString());

        return request;
    }
    private AlbumSearchResponseVo parseSearchResult(SearchResponse<AlbumInfoIndex> response) {
        //  创建查询结果集对象
        AlbumSearchResponseVo responseVO = new AlbumSearchResponseVo();
        // 获取总记录数
        responseVO.setTotal(response.hits().total().value());

        // 解析查询列表
        List<Hit<AlbumInfoIndex>> albumInfoIndexList = response.hits().hits();
        List<AlbumInfoIndexVo> albumInfoIndexVoList = new ArrayList<>();
        for (Hit<AlbumInfoIndex> hit : albumInfoIndexList) {
            AlbumInfoIndexVo albumInfoIndexVo = new AlbumInfoIndexVo();
            BeanUtils.copyProperties(hit.source(), albumInfoIndexVo);
            //处理高亮
            if(null !=  hit.highlight().get("albumTitle")) {
                albumInfoIndexVo.setAlbumTitle(hit.highlight().get("albumTitle").get(0));
            }
            albumInfoIndexVoList.add(albumInfoIndexVo);
        }
        responseVO.setList(albumInfoIndexVoList);
        return responseVO;
    }
}
