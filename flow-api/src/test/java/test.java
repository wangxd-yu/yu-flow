import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.Map;

public class test {
    public static void main(String[] args) {
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> root = new HashMap<>();
        root.put("headers", "myHeaders");
        map.put("res", root);
        System.out.println("map=" + map);
        try {
            Object obj = JsonPath.read(map, "$.res");
            System.out.println("obj=" + obj);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
