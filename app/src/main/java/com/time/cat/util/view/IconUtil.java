/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.time.cat.util.view;

import android.content.Context;
import android.graphics.Color;

import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.IIcon;

/**
 * Created by joseangel.pineiro on 10/29/15.
 */
public class IconUtil {

    public static IconicsDrawable icon_white_24dp(Context ctx, IIcon ic, int size) {
        return icon_white(ctx, ic, 24);
    }

    public static IconicsDrawable icon_white(Context ctx, IIcon ic, int size) {
        return icon(ctx, ic, Color.WHITE, size);
    }

    public static IconicsDrawable icon(Context ctx, IIcon ic, int color) {
        return new IconicsDrawable(ctx, ic).sizeDp(48).paddingDp(2).colorRes(color);
    }

    public static IconicsDrawable icon(Context ctx, IIcon ic, int color, int size) {
        return new IconicsDrawable(ctx, ic).sizeDp(size).paddingDp(0).colorRes(color);
    }
}
