package org.yu.flow.login;

/**
 * 登录日志里的 IP 归属地。默认可执行包不带离线库（{@code -Pwith-ip2region} 才打包），
 * 没有实现时地区记为「未知」；宿主也可以自己提供一个 Bean 接入别的 IP 库。
 */
public interface IpRegionResolver {

    /**
     * @return 形如「中国-浙江省-杭州市-电信」的地区；查不到返回 null
     */
    String resolve(String ip);
}
