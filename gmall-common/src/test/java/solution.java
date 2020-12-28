import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class solution {
    /**
     * 当前元素入队。
     * 当前元素大于等于队列头，则还要删除头。
     * @param args
     */
    public static void main(String[] args) {
        int[] nums = {1, 3, -1, -3, 5, 3, 6, 7};
        int k = 3;

        ArrayList<Integer> deque = new ArrayList<>();    // 维持一个单调减的双端队列
        List<Integer> ans = new ArrayList<>();      // 结果集
        deque.add(nums[0]);
        if ( nums[1] >= deque.get(0)) {
            deque.remove(0);
        }
        deque.add(nums[1]);
        if ( nums[2] >= deque.get(0)) {
            deque.remove(0);
        }
        deque.add(nums[2]);
        int i = 0;
        //ans.add(deque.);
        while ( i + k <= nums.length) {

        }
    }
}
