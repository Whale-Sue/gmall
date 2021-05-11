package atguigu;

import com.atguigu.common.utils.RedisUtils;
import com.atguigu.modules.sys.entity.SysUserEntity;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class RedisTest {
	@Autowired
	private RedisUtils redisUtils;

	@Test
	public void contextLoads() {
		SysUserEntity user = new SysUserEntity();
		user.setEmail("qqq@qq.com");
		redisUtils.set("user", user);

		System.out.println(ToStringBuilder.reflectionToString(redisUtils.get("user", SysUserEntity.class)));
	}

	@Test
	public void testCherryPick() {
		System.out.println("this is before cherry pick, plz ignore this commit");
	}

	@Test
	public void realCherryPick() {
		System.out.println("this is real cherry pick, you should accept this commit");
	}

	@Test
	public void realCherryPick2() {
		System.out.println("this is real cherry pick again, you should accept this commit");
	}
}
