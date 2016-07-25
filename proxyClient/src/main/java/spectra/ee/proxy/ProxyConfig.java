package spectra.ee.proxy;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * proxySimple.properties의 설정 정보를 가져온다.
 * 
 * @author jhkim
 */
public class ProxyConfig
{
	private static final Properties CONFIG_MAP = new Properties();
	
	public static void load(String path) throws IOException
	{
        FileInputStream fis = new FileInputStream(path);
        CONFIG_MAP.load(fis);
        fis.close();
	}
	
	public static String getConf(String key, String defaultValue)
	{
		String value = CONFIG_MAP.getProperty(key);
		return value == null ? defaultValue : value;
	}
	
	public static int getConfInt(String key, int defaultValue)
	{
		String value = CONFIG_MAP.getProperty(key);
		return value == null ? defaultValue : Integer.parseInt(value);
	}
	
	public static String getConf(String key)
	{
		return getConf(key, "");
	}
	
	public static int getConfInt(String key)
	{
		return getConfInt(key, -1);
	}
	
	public static String getDefaultStr(String str, String defStr)
	{
		if( str == null || "".equals(str) )
		{
			return defStr;
		}
		else
		{
			return str;
		}
	}
}
