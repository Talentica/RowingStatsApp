package com.talentica.rowingapp.ui.roll;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.talentica.rowingapp.R;
import com.talentica.rowingapp.common.Pair;

class RollView extends FrameLayout {
	
	enum ValueType {
		MAX,
		AVG,
		CUR
	}
	
	enum Mode {
		SMALL,
		BIG
	}
	
	private final Pair<ValueType, ValueType> rollValueTypes;
	
	private Mode mode = Mode.SMALL;
	
	private RollViewlet primaryView;
	private RollViewlet secondaryView;

	private TextView type_text;
	
	RollView(Context context, Pair<ValueType, ValueType> rollValueTypes) {
		super(context);
		
		this.rollValueTypes = rollValueTypes;
	}
	
	void setup(LayoutInflater inflater) {
		View layout = inflater.inflate(R.layout.roll_view2, null);
		addView(layout, new LayoutParams(
				LayoutParams.MATCH_PARENT,
				LayoutParams.MATCH_PARENT));
		this.type_text = (TextView) layout.findViewById(R.id.roll_type_lable);
		this.primaryView = new RollViewlet(getContext(), layout.findViewById(R.id.roll_primary_view)); 
		this.secondaryView = new RollViewlet(getContext(), layout.findViewById(R.id.roll_secondary_view));
	}

	
	public Mode getMode() {
		return mode;
	}

	public void setMode(Mode mode) {
		
		if (rollValueTypes.second == null) { // CURRENT roll does not have a secondary roll value to display
			mode = Mode.SMALL;
		}
		
		secondaryView.hide(mode != Mode.BIG);
		
		switch (mode) {
		case BIG:
			primaryView.setLabel(rollValueTypes.first.name().toLowerCase());
			secondaryView.setLabel(rollValueTypes.second.name().toLowerCase());
			break;
		case SMALL:
			primaryView.setLabel(null);
			break;
		}
		
		this.mode = mode;
	}

	void setLabel(String s) {
		type_text.setText(s);
	}
	
	public void setRoll(final float ... rollValues) {
		switch (mode) {
		case BIG:
			primaryView.setRoll(rollValues[0]);
			secondaryView.setRoll(rollValues[1]);
			break;
		case SMALL:
			primaryView.setRoll(rollValues);			
		}
	}	
}
