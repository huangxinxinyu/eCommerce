package com.xinyu.ecommerce.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyu.ecommerce.store.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
