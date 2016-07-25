package spectra.ee.proxy.util;

import java.util.Date;

public class ProxyUtil
{
	/**
	 * Guid를 생성하여 리턴한다.
	 */
	public static String getGuid()
	{
	    StringBuilder szGuid = new StringBuilder();
	    String szUid = new java.rmi.server.UID().toString();
	    Date date = new Date();
	
	    long lTime = date.getTime();
	    szGuid.append(szUid).append(lTime);
	
	    StringBuilder sbResult = new StringBuilder(szGuid.toString());
	    if(sbResult.length() < 36)
	    {
	        for(int i = sbResult.length(); i < 37; i++)
	        {
	            sbResult.append("0");
	        }
	    }
	
	    String szOutResult = sbResult.substring(0, 36);
	    szOutResult = replace(szOutResult, new char[] {':', '-'}, '_');
	
	    return szOutResult;
	}

	private static String replace(String str, char[] oldChars, char newChar)
	{
		int count = str.length();
		int len = count;
		int i = -1;
		char[] val = str.toCharArray();
	
		while (++i < len)
		{
			if (val[i] == oldChars[0] || val[i] == oldChars[1])
			{
				break;
			}
		}
	
		if (i < len)
		{
			char buf[] = new char[len];
			System.arraycopy(val, 0, buf, 0, val.length);
			while (i < len)
			{
				char c = val[i];
				buf[i] = (c == oldChars[0] || c == oldChars[1]) ? newChar : c;
				i++;
			}
			return new String(buf);
		}
		
		return str;
	}
	
	/**
     * 주어진 문자가 null이면 "" 리턴함.
     *
     * @param str 문자열
     *
     * @return 주어진 문자가 null이면 ""를 리턴. 아니면 주어진 문자열을 리턴.
     */
    public static String defaultIfBlank(String str)
    {
        return defaultIfBlank(str, "");
    }

    /**
     *  주어진 문자가 null이면 defaultValue를 리턴함.
     *
     * @param str 문자열
     * @param defaultStr str이 null일때 리턴할 문자.
     *
     * @return 주어진 문자가 null이면 defaultValue를 리턴. 아니면 주어진 문자열을 리턴.
     */
    public static String defaultIfBlank(String str, String defaultStr)
    {
        return isBlank(str) ? defaultStr : str;
    }
    
    public static boolean isBlank(String str) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
