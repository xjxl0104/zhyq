package com.zhyq.park.hui.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhyq.park.common.result.PageResult;
import com.zhyq.park.common.result.Result;
import com.zhyq.park.hui.entity.Product;
import com.zhyq.park.hui.mapper.ProductMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "惠企服务-商城商品")
@RestController
@RequestMapping("/service/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductMapper productMapper;

    @Operation(summary = "分页查询商品")
    @GetMapping("/page")
    public Result<PageResult<Product>> page(@RequestParam(defaultValue = "1") int pageNo,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String name,
                                            @RequestParam(required = false) String productType,
                                            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), Product::getName, name)
          .eq(StringUtils.hasText(productType), Product::getProductType, productType)
          .eq(status != null, Product::getStatus, status)
          .orderByDesc(Product::getId);
        IPage<Product> p = productMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Operation(summary = "商品详情")
    @GetMapping("/{id}")
    public Result<Product> get(@PathVariable Long id) {
        return Result.ok(productMapper.selectById(id));
    }

    @Operation(summary = "新增商品")
    @PostMapping
    public Result<Long> add(@RequestBody Product product) {
        productMapper.insert(product);
        return Result.ok(product.getId());
    }

    @Operation(summary = "修改商品")
    @PutMapping
    public Result<Void> update(@RequestBody Product product) {
        productMapper.updateById(product);
        return Result.ok();
    }

    @Operation(summary = "删除商品")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productMapper.deleteById(id);
        return Result.ok();
    }
}
