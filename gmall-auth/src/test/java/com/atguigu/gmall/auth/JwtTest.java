package com.atguigu.gmall.auth;

import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.RsaUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {

    // 别忘了创建D:\\project\rsa目录
    private static final String pubKeyPath = "E:\\004_Java_Study\\001_MyWork\\006_OnlineMall\\01_project\\rsa\\rsa.pub";
    private static final String priKeyPath = "E:\\004_Java_Study\\001_MyWork\\006_OnlineMall\\01_project\\rsa\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "234");
    }

    //    @BeforeEach
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "11");
        map.put("username", "liuyan");
        // 生成token
        String token = JwtUtils.generateToken(map, privateKey, 5);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6IjExIiwidXNlcm5hbWUiOiJsaXV5YW4iLCJleHAiOjE2MDU2MDM4ODZ9.cNypQ4RJn25oM2MEw-Jj-EyAdMF4ytsLvxRNGIls3CNokzG9j8HtiihWLyYnEDzUqvZIW1bislx5Jgjh7s1yRj5TpIJyPdQ1OVEJEkouXrggnFNk608do_z9u8MfiyaswGZ-lioaCii1dnXsW0PR9Ryzy3E0X7DU9DldVq9P1hZM9T0xcZZIcGX9gh6CXG2gGfxVJpDjwQD027cH19bQevV2KC7mYeltXonlVHI1a-F8YUaarX1M83Hz_JFCePIVrSMMIQucjK1UA4SRdbcqdBQXRaA3amE_EjFIaQXlB77kyr-h-SIKOXFpw-EQjn-ExMUJzzkqgdHY4yKDtdv1qA";

        // 解析token
        Map<String, Object> map = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + map.get("id"));
        System.out.println("userName: " + map.get("username"));
    }
}
