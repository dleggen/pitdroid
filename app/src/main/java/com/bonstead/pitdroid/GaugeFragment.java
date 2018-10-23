package com.bonstead.pitdroid;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.bonstead.pitdroid.HeaterMeter.NamedSample;

public class GaugeFragment extends Fragment implements HeaterMeter.Listener, SharedPreferences.OnSharedPreferenceChangeListener
{
	private GaugeView mGauge;
	private GaugeHandView[] mProbeHands = new GaugeHandView[HeaterMeter.kNumProbes];
	private GaugeHandView mSetPoint;

	private HeaterMeter mHeaterMeter;
	private TextView mLastUpdate;
	private int mServerTime = 0;
	private Time mTime = new Time();
	private boolean mSettingPit = false;

	@Override
	public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.fragment_gauge, container, false);

		mHeaterMeter = ((PitDroidApplication) getActivity().getApplication()).mHeaterMeter;

		mGauge = (GaugeView) view.findViewById(R.id.thermometer);
		mProbeHands[0] = (GaugeHandView) view.findViewById(R.id.pitHand);
		mProbeHands[1] = (GaugeHandView) view.findViewById(R.id.probe1Hand);
		mProbeHands[2] = (GaugeHandView) view.findViewById(R.id.probe2Hand);
		mProbeHands[3] = (GaugeHandView) view.findViewById(R.id.probe3Hand);

		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
		{
			LinearLayout masterLayout = (LinearLayout) view.findViewById(R.id.masterLayout);
			masterLayout.setOrientation(LinearLayout.HORIZONTAL);

			LinearLayout gaugeLayout = (LinearLayout) view.findViewById(R.id.gaugeLayout);
			gaugeLayout.setOrientation(LinearLayout.HORIZONTAL);

			LinearLayout buttonLayout = (LinearLayout) view.findViewById(R.id.buttonLayout);
			buttonLayout.setOrientation(LinearLayout.VERTICAL);
		}

		mSetPoint = (GaugeHandView) view.findViewById(R.id.setPoint);
		mSetPoint.mListener = new GaugeHandView.Listener()
		{
			@Override
			public void onValueChanged(final float value)
			{
				mSettingPit = true;

				View setTempView = inflater.inflate(R.layout.dialog_settemp, null);

				final NumberPicker picker = (NumberPicker)setTempView.findViewById(R.id.temperature);
				picker.setMinValue(mGauge.getMinValue());
				picker.setMaxValue(mGauge.getMaxValue());
				picker.setValue((int)value);

				AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
				builder.setView(setTempView);
				builder.setTitle("New pit set temp");
				builder.setPositiveButton("Set", new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int id)
					{
						final int newTemp = picker.getValue();

						Thread trd = new Thread(new Runnable()
						{
							@Override
							public void run()
							{
								mHeaterMeter.changePitSetTemp(newTemp);
								mSettingPit = false;
							}
						});
						trd.start();
					}
				})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int id)
					{
						mSettingPit = false;
					}
				})
				.create().show();
			}
		};

		mLastUpdate = (TextView) view.findViewById(R.id.lastUpdate);

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		mHeaterMeter.addListener(this);

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplication().getBaseContext());
		updatePrefs(prefs);
		prefs.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onPause()
	{
		super.onPause();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplication().getBaseContext());
		prefs.unregisterOnSharedPreferenceChangeListener(this);

		mHeaterMeter.removeListener(this);
	}

	@Override
	public void samplesUpdated(final NamedSample latestSample)
	{
		if (latestSample != null)
		{
			for (int p = 0; p < HeaterMeter.kNumProbes; p++)
			{
				if (Double.isNaN(latestSample.mProbes[p]))
				{
					mProbeHands[p].setVisibility(View.GONE);
					mProbeHands[p].setHandTarget(0.f);
				}
				else
				{
					mProbeHands[p].setVisibility(View.VISIBLE);
					mProbeHands[p].setHandTarget((float) latestSample.mProbes[p]);
				}

				// Don't set the name on the pit temp hand, or any probes that aren't connected, so
				// we wont' show them on the legend
				if (p > 0 && !Double.isNaN(latestSample.mProbes[p]))
				{
					mProbeHands[p].setName(latestSample.mProbeNames[p]);
				}
			}

			if (!Double.isNaN(latestSample.mSetPoint) && !mSetPoint.isDragging() && !mSettingPit)
				mSetPoint.setHandTarget((float)latestSample.mSetPoint);

			// Update the last update time
			if (mServerTime < latestSample.mTime)
			{
				mTime.setToNow();
				mLastUpdate.setText(mTime.format("%r"));
				mServerTime = latestSample.mTime;
			}
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		updatePrefs(sharedPreferences);
	}

	private void updatePrefs(SharedPreferences sharedPreferences)
	{
		int minTemp = Integer.valueOf(sharedPreferences.getString(SettingsFragment.KEY_MIN_TEMP, "50"));
		int maxTemp = Integer.valueOf(sharedPreferences.getString(SettingsFragment.KEY_MAX_TEMP, "350"));

		mGauge.updateRange(minTemp, maxTemp);
	}
}