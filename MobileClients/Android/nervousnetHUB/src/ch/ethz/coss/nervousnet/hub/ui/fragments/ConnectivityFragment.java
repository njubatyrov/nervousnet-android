/*******************************************************************************
 *
 *  *     Nervousnet - a distributed middleware software for social sensing. 
 *  *      It is responsible for collecting and managing data in a fully de-centralised fashion
 *  *
 *  *     Copyright (C) 2016 ETH Zürich, COSS
 *  *
 *  *     This file is part of Nervousnet Framework
 *  *
 *  *     Nervousnet is free software: you can redistribute it and/or modify
 *  *     it under the terms of the GNU General Public License as published by
 *  *     the Free Software Foundation, either version 3 of the License, or
 *  *     (at your option) any later version.
 *  *
 *  *     Nervousnet is distributed in the hope that it will be useful,
 *  *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  *     GNU General Public License for more details.
 *  *
 *  *     You should have received a copy of the GNU General Public License
 *  *     along with NervousNet. If not, see <http://www.gnu.org/licenses/>.
 *  *
 *  *
 *  * 	Contributors:
 *  * 	Prasad Pulikal - prasad.pulikal@gess.ethz.ch  -  Initial API and implementation
 *******************************************************************************/
/**
 * 
 */
package ch.ethz.coss.nervousnet.hub.ui.fragments;

import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import ch.ethz.coss.nervousnet.hub.R;
import ch.ethz.coss.nervousnet.hub.ui.views.ConnectivitySensorView;
import ch.ethz.coss.nervousnet.lib.ConnectivityReading;
import ch.ethz.coss.nervousnet.lib.ErrorReading;
import ch.ethz.coss.nervousnet.lib.SensorReading;
import ch.ethz.coss.nervousnet.lib.Utils;
import ch.ethz.coss.nervousnet.vm.NNLog;

/**
 * @author prasad
 *
 */
public class ConnectivityFragment extends BaseFragment {
	
	private ConnectivitySensorView connectViz;

	public ConnectivityFragment() {
	}

	public ConnectivityFragment(int type) {
		super(type);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_connectivity, container, false);
		connectViz = (ConnectivitySensorView)rootView.findViewById(R.id.connectVizView);
		return rootView;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * ch.ethz.coss.nervousnet.sample.BaseFragment#updateReadings(ch.ethz.coss.
	 * nervousnet.vm.SensorReading)
	 */
	@Override
	public void updateReadings(SensorReading reading) {

		NNLog.d("ConnectivityFragment", "Inside updateReadings");

		if (reading instanceof ErrorReading) {

			NNLog.d("ConnectivityFragment", "Inside updateReadings - ErrorReading");
			handleError((ErrorReading) reading);
		} else {
			TextView isConnectedTV = (TextView) getActivity().findViewById(R.id.isConnectedTV);
			TextView netwType = (TextView) getActivity().findViewById(R.id.netwType);
			TextView isRoaming = (TextView) getActivity().findViewById(R.id.isRoaming);

			isConnectedTV.setText("" + (((ConnectivityReading) reading).isConnected() ? "Yes" : "No"));
			netwType.setText("" + Utils.getConnectivityTypeString(((ConnectivityReading) reading).getNetworkType()));
			isRoaming.setText("" + ((((ConnectivityReading) reading).isRoaming()) ? "Yes" : "No"));
			
			// boolean variables for wifi, data, roaming
			boolean wifi = (((ConnectivityReading) reading).getNetworkType())==ConnectivityManager.TYPE_WIFI;
			boolean data = (((ConnectivityReading) reading).getNetworkType())==ConnectivityManager.TYPE_MOBILE;
			boolean roaming = (((ConnectivityReading) reading).isRoaming());
			connectViz.setConnectivityValues(wifi, data, roaming);
		}
	}

	@Override
	public void handleError(ErrorReading reading) {
		NNLog.d("ConnectivityFragment", "handleError called");
		TextView status = (TextView) getActivity().findViewById(R.id.sensor_status_conn);
		status.setText("Error: code = " + reading.getErrorCode() + ", message = " + reading.getErrorString());
	}
}
