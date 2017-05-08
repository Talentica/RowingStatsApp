package com.talentica.rowingapp.ui.android;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;

import com.talentica.rowingapp.ui.utils.RSCanvas;
import com.talentica.rowingapp.ui.utils.RSPaint;
import com.talentica.rowingapp.ui.utils.RSPath;
import com.talentica.rowingapp.ui.utils.RSRect;

public class AndroidCanvas implements RSCanvas {

	private Canvas canvas;
	
	
	public AndroidCanvas(Canvas canvas) {
		this.canvas = canvas;
	}

	public AndroidCanvas setCanvas(Canvas canvas) {
		this.canvas = canvas;
		return this;
	}
	
	@Override
	public void drawLine(int left, float y, int right, float y2,
			RSPaint gridPaint) {
		canvas.drawLine(left, y, right, y2, (Paint) gridPaint);

	}

	@Override
	public void drawPath(RSPath path, RSPaint strokePaint) {
		canvas.drawPath((Path) path, (Paint) strokePaint);

	}

	@Override
	public void drawRect(int left, int top, int right, int bottom,
			RSPaint paint) {
		canvas.drawRect(left, top, right, bottom, (Paint) paint);

	}

	@Override
	public RSRect getClipBounds() {
		Rect r = canvas.getClipBounds();
		
		RSRect res = new RSRect();
		
		res.bottom = r.bottom;
		res.top = r.top;
		res.left = r.left;
		res.right = r.right;
		
		return res;
	}
}
