package com.atguigu.gmall.sms.api;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

public interface GmallSmsApi {

    /**
     * 根据skuId查询打折、优惠、满减信息
     * @param skuId
     * @return
     */
    @GetMapping(value = "sms/skubounds/sales/sku/{skuId}")
    public ResponseVo<List<ItemSaleVo>> querySalesBySkuId(@PathVariable(value = "skuId") Long skuId);

    @PostMapping(value = "sms/skubounds/sales/save")
    public ResponseVo<Object> saveSales(@RequestBody SkuSaleVo skuSaleVo);
}
