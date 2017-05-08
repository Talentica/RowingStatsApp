package com.talentica.rowingapp.common.data.remote;

import com.talentica.rowingapp.common.data.session.SessionRecorderConstants;
import com.talentica.rowingapp.common.data.stroke.AppStroke;
import com.talentica.rowingapp.common.param.ParamKeys;

public class RemoteDataHelper {

	public static String getAddr(AppStroke rs) {
		
		String res = rs.getParameters().getValue(ParamKeys.PARAM_SESSION_BROADCAST_HOST.getId());
		
		return res == null ? SessionRecorderConstants.BROADCAST_HOST : res;
	}

	public static int getPort(AppStroke rs) {
		Integer res = rs.getParameters().getValue(ParamKeys.PARAM_SESSION_BROADCAST_PORT.getId());
		return res == null ? SessionRecorderConstants.BROADCAST_PORT : res;
	}
}
