package ch.ethz.coss.nervousnet.sensors;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import ch.ethz.coss.nervousnet.utils.ValueFormatter;
import ch.ethz.coss.nervousnet.vm.ConnectivityReading;

public class ConnectivitySensor {

	private Context context;
	private ConnectivityReading reading;

	private List<ConnectivitySensorListener> listenerList = new ArrayList<ConnectivitySensorListener>();
	private Lock listenerMutex = new ReentrantLock();

	public void addListener(ConnectivitySensorListener listener) {
		listenerMutex.lock();
		listenerList.add(listener);
		listenerMutex.unlock();
	}

	public void removeListener(ConnectivitySensorListener listener) {
		listenerMutex.lock();
		listenerList.remove(listener);
		listenerMutex.unlock();
	}

	public void clearListeners() {
		listenerMutex.lock();
		listenerList.clear();
		listenerMutex.unlock();
	}

	public interface ConnectivitySensorListener {
		public void connectivitySensorDataReady(ConnectivityReading reading);
	}

	public void dataReady(ConnectivityReading reading) {
		listenerMutex.lock();
		for (ConnectivitySensorListener listener : listenerList) {
			listener.connectivitySensorDataReady(reading);
		}
		listenerMutex.unlock();
	}

	public void runConnectivitySensor() {

		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
		int networkType = -1;
		boolean isRoaming = false;
		if (isConnected) {
			networkType = activeNetwork.getType();
			isRoaming = activeNetwork.isRoaming();
		}

		String wifiHashId = "";
		int wifiStrength = Integer.MIN_VALUE;

		if (networkType == ConnectivityManager.TYPE_WIFI) {
			WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			WifiInfo wi = wm.getConnectionInfo();
			StringBuilder wifiInfoBuilder = new StringBuilder();
			wifiInfoBuilder.append(wi.getBSSID());
			wifiInfoBuilder.append(wi.getSSID());
			try {
				MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
				messageDigest.update(wifiInfoBuilder.toString().getBytes());
				wifiHashId = new String(messageDigest.digest());
			} catch (NoSuchAlgorithmException e) {
			}
			wifiStrength = wi.getRssi();
		}

		byte[] cdmaHashId = new byte[32];
		byte[] lteHashId = new byte[32];
		byte[] gsmHashId = new byte[32];
		byte[] wcdmaHashId = new byte[32];

		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

		List<CellInfo> cis = tm.getAllCellInfo();
		if (cis != null) {
			// New method
			for (CellInfo ci : cis) {
				if (ci.isRegistered()) {
					if (ci instanceof CellInfoCdma) {
						CellInfoCdma cic = (CellInfoCdma) ci;
						cdmaHashId = generateMobileDigestId(cic.getCellIdentity().getSystemId(),
								cic.getCellIdentity().getNetworkId(), cic.getCellIdentity().getBasestationId());
					}
					if (ci instanceof CellInfoGsm) {
						CellInfoGsm cic = (CellInfoGsm) ci;
						gsmHashId = generateMobileDigestId(cic.getCellIdentity().getMcc(),
								cic.getCellIdentity().getMnc(), cic.getCellIdentity().getCid());
					}
					if (ci instanceof CellInfoLte) {
						CellInfoLte cic = (CellInfoLte) ci;
						lteHashId = generateMobileDigestId(cic.getCellIdentity().getMcc(),
								cic.getCellIdentity().getMnc(), cic.getCellIdentity().getCi());
					}
					if (ci instanceof CellInfoWcdma) {
						CellInfoWcdma cic = (CellInfoWcdma) ci;
						wcdmaHashId = generateMobileDigestId(cic.getCellIdentity().getMcc(),
								cic.getCellIdentity().getMnc(), cic.getCellIdentity().getCid());
					}
				}
			}
		} else {
			// Legacy method
			CellLocation cl = tm.getCellLocation();
			if (cl instanceof CdmaCellLocation) {
				CdmaCellLocation cic = (CdmaCellLocation) cl;
				cdmaHashId = generateMobileDigestId(cic.getSystemId(), cic.getNetworkId(), cic.getBaseStationId());
			}
			if (cl instanceof GsmCellLocation) {
				GsmCellLocation cic = (GsmCellLocation) cl;
				gsmHashId = generateMobileDigestId(cic.getLac(), 0, cic.getCid());
			}
		}

		StringBuilder mobileHashBuilder = new StringBuilder();
		mobileHashBuilder.append(new String(cdmaHashId));
		mobileHashBuilder.append(new String(lteHashId));
		mobileHashBuilder.append(new String(gsmHashId));
		mobileHashBuilder.append(new String(wcdmaHashId));

		dataReady(new ConnectivityReading((int) System.currentTimeMillis() / 1000, isConnected, networkType, isRoaming,
				wifiHashId, wifiStrength, mobileHashBuilder.toString()));

	}

	public class ConnectivityTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
			boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
			int networkType = -1;
			boolean isRoaming = false;
			if (isConnected) {
				networkType = activeNetwork.getType();
				isRoaming = activeNetwork.isRoaming();
			}

			String wifiHashId = "";
			int wifiStrength = Integer.MIN_VALUE;

			if (networkType == ConnectivityManager.TYPE_WIFI) {
				WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
				WifiInfo wi = wm.getConnectionInfo();
				StringBuilder wifiInfoBuilder = new StringBuilder();
				wifiInfoBuilder.append(wi.getBSSID());
				wifiInfoBuilder.append(wi.getSSID());
				try {
					MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
					messageDigest.update(wifiInfoBuilder.toString().getBytes());
					wifiHashId = new String(messageDigest.digest());
				} catch (NoSuchAlgorithmException e) {
				}
				wifiStrength = wi.getRssi();
			}

			byte[] cdmaHashId = new byte[32];
			byte[] lteHashId = new byte[32];
			byte[] gsmHashId = new byte[32];
			byte[] wcdmaHashId = new byte[32];

			TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

			List<CellInfo> cis = tm.getAllCellInfo();
			if (cis != null) {
				// New method
				for (CellInfo ci : cis) {
					if (ci.isRegistered()) {
						if (ci instanceof CellInfoCdma) {
							CellInfoCdma cic = (CellInfoCdma) ci;
							cdmaHashId = generateMobileDigestId(cic.getCellIdentity().getSystemId(),
									cic.getCellIdentity().getNetworkId(), cic.getCellIdentity().getBasestationId());
						}
						if (ci instanceof CellInfoGsm) {
							CellInfoGsm cic = (CellInfoGsm) ci;
							gsmHashId = generateMobileDigestId(cic.getCellIdentity().getMcc(),
									cic.getCellIdentity().getMnc(), cic.getCellIdentity().getCid());
						}
						if (ci instanceof CellInfoLte) {
							CellInfoLte cic = (CellInfoLte) ci;
							lteHashId = generateMobileDigestId(cic.getCellIdentity().getMcc(),
									cic.getCellIdentity().getMnc(), cic.getCellIdentity().getCi());
						}
						if (ci instanceof CellInfoWcdma) {
							CellInfoWcdma cic = (CellInfoWcdma) ci;
							wcdmaHashId = generateMobileDigestId(cic.getCellIdentity().getMcc(),
									cic.getCellIdentity().getMnc(), cic.getCellIdentity().getCid());
						}
					}
				}
			} else {
				// Legacy method
				CellLocation cl = tm.getCellLocation();
				if (cl instanceof CdmaCellLocation) {
					CdmaCellLocation cic = (CdmaCellLocation) cl;
					cdmaHashId = generateMobileDigestId(cic.getSystemId(), cic.getNetworkId(), cic.getBaseStationId());
				}
				if (cl instanceof GsmCellLocation) {
					GsmCellLocation cic = (GsmCellLocation) cl;
					gsmHashId = generateMobileDigestId(cic.getLac(), 0, cic.getCid());
				}
			}

			StringBuilder mobileHashBuilder = new StringBuilder();
			mobileHashBuilder.append(new String(cdmaHashId));
			mobileHashBuilder.append(new String(lteHashId));
			mobileHashBuilder.append(new String(gsmHashId));
			mobileHashBuilder.append(new String(wcdmaHashId));

			dataReady(new ConnectivityReading((int) System.currentTimeMillis() / 1000, isConnected, networkType,
					isRoaming, wifiHashId, wifiStrength, mobileHashBuilder.toString()));
			return null;

		}
	}

	private byte[] generateMobileDigestId(int v1, int v2, int v3) {
		StringBuilder cicBuilder = new StringBuilder();
		cicBuilder.append(ValueFormatter.leadingZeroHexUpperString(v1));
		cicBuilder.append(ValueFormatter.leadingZeroHexUpperString(v2));
		cicBuilder.append(ValueFormatter.leadingZeroHexUpperString(v3));
		MessageDigest messageDigest;
		try {
			messageDigest = MessageDigest.getInstance("SHA-256");
			messageDigest.update(cicBuilder.toString().getBytes());
			return messageDigest.digest();
		} catch (NoSuchAlgorithmException e) {
		}
		return new byte[16];
	}

	public void start() {
		handler = new Handler(hthread.getLooper());
		final Runnable run = new Runnable() {
			@Override
			public void run() {
				runConnectivitySensor();
				// new ConnectivityTask().execute();
				handler.postDelayed(this, 5000);
			}

		};

		boolean flag = handler.postDelayed(run, 0);
	}

	HandlerThread hthread;
	Handler handler;

	private ConnectivitySensor(Context context) {
		this.context = context;

		hthread = new HandlerThread("HandlerThread");
		hthread.start();
	}

	public static ConnectivitySensor getInstance(Context context) {

		if (_instance == null)
			_instance = new ConnectivitySensor(context);

		return _instance;
	}

	public static ConnectivitySensor _instance;

}