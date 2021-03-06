package com.time.cat.ui.modules.routines.week_view

import android.content.Intent
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.SparseIntArray
import android.view.*
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.beGone
import com.simplemobiletools.commons.extensions.getContrastColor
import com.simplemobiletools.commons.extensions.onGlobalLayout
import com.time.cat.R
import com.time.cat.config
import com.time.cat.data.Constants.DAY_SECONDS
import com.time.cat.data.Constants.NEW_EVENT_SET_HOUR_DURATION
import com.time.cat.data.Constants.NEW_EVENT_START_TS
import com.time.cat.data.Constants.WEEK_MILLI_SECONDS
import com.time.cat.data.Constants.WEEK_START_TIMESTAMP
import com.time.cat.data.database.DB
import com.time.cat.data.database.RoutineDao
import com.time.cat.data.model.APImodel.Routine
import com.time.cat.data.model.DBmodel.DBRoutine
import com.time.cat.data.network.RetrofitHelper
import com.time.cat.helper.Formatter
import com.time.cat.helper.seconds
import com.time.cat.ui.modules.operate.InfoOperationActivity
import com.time.cat.ui.modules.routines.week_view.listener.WeekCalendarCallback
import com.time.cat.ui.modules.routines.week_view.listener.WeekFragmentListener
import com.time.cat.ui.widgets.weekview.MyScrollView
import com.time.cat.ui.widgets.weekview.WeeklyCalendarImpl
import com.time.cat.util.view.ViewUtil.dp2px
import kotlinx.android.synthetic.main.fragment_weekview.*
import kotlinx.android.synthetic.main.fragment_weekview.view.*
import org.joda.time.DateTime
import org.joda.time.Days
import rx.Subscriber
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.sql.SQLException


/**
 * @author dlink
 * @email linxy59@mail2.sysu.edu.cn
 * @date 2018/3/15
 * @description null
 * @usage null
 */
class WeekFragment : Fragment(), WeekCalendarCallback {

    private val CLICK_DURATION_THRESHOLD = 150
    private val PLUS_FADEOUT_DELAY = 5000L

    var mListener: WeekFragmentListener? = null
    private var firstDayOfWeek = 0L
    private var mRowHeight = 0
    private var minScrollY = -1
    private var maxScrollY = -1
    private var mWasDestroyed = false
    private var primaryColor = 0
    private var lastHash = 0
    private var isFragmentVisible = false
    private var wasFragmentInit = false
    private var wasExtraHeightAdded = false
    private var clickStartTime = 0L
    private var selectedGrid: View? = null
    private var todayColumnIndex = -1
    private var dbRoutines = ArrayList<DBRoutine>()
    private var allDayHolders = ArrayList<RelativeLayout>()
    private var allDayRows = ArrayList<HashSet<Int>>()
    private var eventTypeColors = SparseIntArray()

    lateinit var inflater: LayoutInflater
    lateinit var mainView: View
    lateinit var mScrollView: MyScrollView
    lateinit var mCalendar: WeeklyCalendarImpl
    lateinit var mRes: Resources

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        context!!.dbHelper.getEventTypes {
//            it.map { eventTypeColors.put(it.id, it.color) }
//        }//TODO
        eventTypeColors.put(0,  context!!.config.backgroundColor)
        mRowHeight = (context!!.resources.getDimension(R.dimen.weekly_view_row_height)).toInt()
        minScrollY = mRowHeight * context!!.config.startWeeklyAt
        maxScrollY = mRowHeight * context!!.config.endWeeklyAt

        firstDayOfWeek = arguments!!.getLong(WEEK_START_TIMESTAMP)
//        primaryColor = context!!.getAdjustedPrimaryColor()
        primaryColor = context!!.config.weekViewSuppressColor
        mRes = resources
        allDayRows.add(HashSet())
        mCalendar = WeeklyCalendarImpl(this, context!!)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.inflater = inflater

        mainView = inflater.inflate(R.layout.fragment_weekview, container, false)
        mScrollView = mainView.week_events_scrollview
        mScrollView.setOnScrollviewListener(object : MyScrollView.ScrollViewListener {
            override fun onScrollChanged(scrollView: MyScrollView, x: Int, y: Int, oldx: Int, oldy: Int) {
                checkScrollLimits(y)
            }
        })

        mScrollView.onGlobalLayout {
            updateScrollY(Math.max(mListener?.getCurrScrollY() ?: 0, minScrollY))
        }

        (0..6).map { inflater.inflate(R.layout.stroke_vertical_divider, mainView.week_vertical_grid_holder) }
        (0..23).map { inflater.inflate(R.layout.stroke_horizontal_divider, mainView.week_horizontal_grid_holder) }

        wasFragmentInit = true
        return mainView
    }

    override fun onPause() {
        super.onPause()
        wasExtraHeightAdded = true
    }

    override fun onResume() {
        super.onResume()
        setupDayLabels()
        updateCalendar()

        mScrollView.onGlobalLayout {
            if (context == null) {
                return@onGlobalLayout
            }

            minScrollY = mRowHeight * context!!.config.startWeeklyAt
            maxScrollY = mRowHeight * context!!.config.endWeeklyAt

            val bounds = Rect()
            week_events_holder.getGlobalVisibleRect(bounds)
            maxScrollY -= bounds.bottom - bounds.top
            if (minScrollY > maxScrollY)
                maxScrollY = -1

            checkScrollLimits(mScrollView.scrollY)
        }
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        isFragmentVisible = menuVisible
        if (isFragmentVisible && wasFragmentInit) {
            mListener?.updateHoursTopMargin(mainView.week_top_holder.height)
            checkScrollLimits(mScrollView.scrollY)
        }
    }

    fun updateCalendar() {
        mCalendar.updateWeeklyCalendar(firstDayOfWeek)
    }

    private fun setupDayLabels() {
        var curDay = Formatter.getDateTimeFromTS(firstDayOfWeek)
        val textColor = context!!.config.weekViewTextColor
        val todayCode = Formatter.getDayCodeFromDateTime(DateTime())

        for (i in 0..6) {
            val dayCode = Formatter.getDayCodeFromDateTime(curDay)
            val dayLetter = getDayLetter(curDay.dayOfWeek)
            mainView.findViewById<TextView>(mRes.getIdentifier("week_day_label_$i", "id", context!!.packageName)).apply {
                text = "$dayLetter\n${curDay.dayOfMonth}"
                textSize = 14F
                setTextColor(if (todayCode == dayCode) primaryColor else textColor)
                alpha = if (todayCode == dayCode) 1.0F else 0.3F
                if (todayCode == dayCode) {
                    todayColumnIndex = i
                }
            }
            curDay = curDay.plusDays(1)
        }
    }

    private fun getDayLetter(pos: Int): String {
        return mRes.getString(when (pos) {
            1 -> R.string.monday_letter
            2 -> R.string.tuesday_letter
            3 -> R.string.wednesday_letter
            4 -> R.string.thursday_letter
            5 -> R.string.friday_letter
            6 -> R.string.saturday_letter
            else -> R.string.sunday_letter
        })
    }

    private fun checkScrollLimits(y: Int) {
        if (minScrollY != -1 && y < minScrollY) {
            mScrollView.scrollY = minScrollY
        } else if (maxScrollY != -1 && y > maxScrollY) {
            mScrollView.scrollY = maxScrollY
        } else if (isFragmentVisible) {
            mListener?.scrollTo(y)
        }
    }

    private fun initGrid() {

        (0..6).map { getColumnWithId(it) }
                .forEachIndexed { index, layout ->
                    layout.removeAllViews()
                    layout.setOnTouchListener { _, motionEvent ->
                        checkGridClick(motionEvent, index, layout)
                        true
                    }
                }
    }

    private fun checkGridClick(event: MotionEvent, index: Int, view: ViewGroup) {

        when (event.action) {
            MotionEvent.ACTION_DOWN -> clickStartTime = System.currentTimeMillis()
            MotionEvent.ACTION_UP -> {

                if (System.currentTimeMillis() - clickStartTime < CLICK_DURATION_THRESHOLD) {

                    selectedGrid?.animation?.cancel()
                    selectedGrid?.beGone()
                    val rowHeight = resources.getDimension(R.dimen.weekly_view_row_height)
                    val hour = (event.y / rowHeight).toInt()
                    selectedGrid = (inflater.inflate(R.layout.week_grid_item, null, false) as ImageView).apply {
                        view.addView(this)
                        background = ColorDrawable(primaryColor)
                        layoutParams.width = view.width
                        layoutParams.height = rowHeight.toInt()
                        y = hour * rowHeight
                        applyColorFilter(primaryColor.getContrastColor())

                        setOnClickListener {
                            val timestamp = firstDayOfWeek + index * DAY_SECONDS + hour * 60 * 60
                            Intent(context, InfoOperationActivity::class.java).apply {
                                putExtra(NEW_EVENT_START_TS, timestamp)
                                putExtra(NEW_EVENT_SET_HOUR_DURATION, true)
                                startActivity(this)
                            }//TODO
                        }
                        animate().alpha(0f).setStartDelay(PLUS_FADEOUT_DELAY).withEndAction {
                            beGone()
                        }
                    }
                }
            }
            else -> {}
        }
    }

    override fun updateWeeklyCalendar(dbRoutines: ArrayList<DBRoutine>) {
        val newHash = dbRoutines.hashCode()
        if (newHash == lastHash) {
            return
        }

        lastHash = newHash
        this.dbRoutines = dbRoutines
        updateEvents()
    }

    private fun updateEvents() {
        if (mWasDestroyed) {
            return
        }
        activity!!.runOnUiThread {
            if (context != null && isAdded) {
                addEvents()
            }
        }
    }

    private fun addEvents() {
        val filtered = RoutineDao.filter(dbRoutines)

        initGrid()
        allDayHolders.clear()
        allDayRows.clear()
        allDayRows.add(HashSet())
        week_all_day_holder?.removeAllViews()

        addNewLine()

        val fullHeight = mRes.getDimension(R.dimen.weekly_view_events_height)
        val minuteHeight = fullHeight / (24 * 60)
        var hadAllDayEvent = false
        val replaceDescription = context!!.config.replaceDescription
//        addCurrentTimeIndicator(minuteHeight)
        mainView.postDelayed( { addCurrentTimeIndicator(minuteHeight) }, 50)

        if (filtered == null) return
        val sorted = filtered.sortedWith(compareBy({ it.beginTs }, { it.endTs }, { it.title }, { if (replaceDescription) it.content else it.content }))
        for (dbRoutine in sorted) {
            if (dbRoutine.getIs_all_day()) {
                hadAllDayEvent = true
                mainView.postDelayed( { addAllDayView(dbRoutine) }, 50)
            } else {
                mainView.postDelayed( { addNormalView(dbRoutine)  }, 50)
            }
        }

        if (!hadAllDayEvent) {
            checkTopHolderHeight()
        }
    }

    private fun addNewLine() {
        val allDaysLine = inflater.inflate(R.layout.week_all_day_events_holder_line, null, false) as RelativeLayout
        week_all_day_holder.addView(allDaysLine)
        allDayHolders.add(allDaysLine)
    }

    private fun addCurrentTimeIndicator(minuteHeight: Float) {
        if (todayColumnIndex != -1) {
            val minutes = DateTime().minuteOfDay
            val todayColumn = getColumnWithId(todayColumnIndex)
            (inflater.inflate(R.layout.week_now_marker, null, false) as ImageView).apply {
                applyColorFilter(primaryColor)
                mainView.week_events_holder.addView(this, 0)
                val extraWidth = (todayColumn.width * 0.3).toInt()
                val markerHeight = resources.getDimension(R.dimen.weekly_view_now_height).toInt()
                (layoutParams as RelativeLayout.LayoutParams).apply {
                    width = todayColumn.width + extraWidth
                    height = markerHeight
                }
                x = todayColumn.x - extraWidth / 2
                y = minutes * minuteHeight - markerHeight / 2
            }
        }
    }

    private fun checkTopHolderHeight() {
        mainView.week_top_holder.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                mainView.week_top_holder.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (isFragmentVisible && activity != null) {
                    mListener?.updateHoursTopMargin(mainView.week_top_holder.height)
                }
            }
        })
    }

    private fun addAllDayView(dbRoutine: DBRoutine) {
        (inflater.inflate(R.layout.week_all_day_event_marker, null, false) as TextView).apply {
            if (activity == null)
                return

            val backgroundColor = getColorFromLabel(dbRoutine.label)//eventTypeColors.get(dbRoutine.eventType, primaryColor)
            background = ColorDrawable(backgroundColor)
            /* 白色带圆角形状的背景 */
//            val adImageBackground = GradientDrawable()
//            adImageBackground.shape = GradientDrawable.RECTANGLE
//            adImageBackground.setColor(backgroundColor)
//            adImageBackground.alpha = 200
//            adImageBackground.cornerRadius = dp2px(3F).toFloat()
//            background = adImageBackground

            setTextColor(backgroundColor.getContrastColor())
            text = dbRoutine.title

            val startDateTime = Formatter.getDateTimeFromTS(dbRoutine.beginTs)
            val endDateTime = Formatter.getDateTimeFromTS(dbRoutine.endTs)

            val minTS = Math.max(startDateTime.seconds(), firstDayOfWeek)
            val maxTS = Math.min(endDateTime.seconds(), firstDayOfWeek + WEEK_MILLI_SECONDS)
            if (minTS > maxTS) return
//            val repeatDuration = dbRoutine.repeatInterval * 1000 //转为毫秒
            val startDateTimeInWeek = Formatter.getDateTimeFromTS(minTS)
            val firstDayIndex = (startDateTimeInWeek.dayOfWeek - if (context!!.config.isSundayFirst) 0 else 1) % 7
            val daysCnt = Days.daysBetween(Formatter.getDateTimeFromTS(minTS).toLocalDate(), Formatter.getDateTimeFromTS(maxTS).toLocalDate()).days

            var doesEventFit: Boolean
            val cnt = allDayRows.size - 1
            var wasEventHandled = false
            var drawAtLine = 0
            for (index in 0..cnt) {
                doesEventFit = true
                drawAtLine = index
                val row = allDayRows[index]
                for (i in firstDayIndex..firstDayIndex + daysCnt) {
                    if (row.contains(i)) {
                        doesEventFit = false
                    }
                }

                for (dayIndex in firstDayIndex..firstDayIndex + daysCnt) {
                    if (doesEventFit) {
                        row.add(dayIndex)
                        wasEventHandled = true
                    } else if (index == cnt) {
                        if (allDayRows.size == index + 1) {
                            allDayRows.add(HashSet<Int>())
                            addNewLine()
                            drawAtLine++
                            wasEventHandled = true
                        }
                        allDayRows.last().add(dayIndex)
                    }
                }
                if (wasEventHandled) {
                    break
                }
            }

            allDayHolders[drawAtLine].addView(this)
            (layoutParams as RelativeLayout.LayoutParams).apply {
                topMargin = mRes.getDimension(R.dimen.tiny_margin).toInt()
                leftMargin = getColumnWithId(firstDayIndex).x.toInt()
                bottomMargin = 1
                val id = firstDayIndex + daysCnt
                if (id < 0) return
                width = getColumnWithId(Math.min(id, 6)).right - leftMargin - 1
            }

            calculateExtraHeight()

            setOnClickListener {
                Intent(context, InfoOperationActivity::class.java).apply {
                    putExtra(InfoOperationActivity.TO_UPDATE_ROUTINE, dbRoutine)
                    startActivity(this)
                }
            }

            setOnLongClickListener {
                MaterialDialog.Builder(activity!!)
                        .content("确定删除这个任务吗？")
                        .positiveText("删除")
                        .onPositive { _, _ ->
                            try {
                                DB.routines().delete(dbRoutine)
                            } catch (e: SQLException) {
                                e.printStackTrace()
                            }

                            RetrofitHelper.getRoutineService().deleteRoutineByUrl(dbRoutine.getUrl())
                                    .subscribeOn(Schedulers.newThread())//请求在新的线程中执行
                                    .observeOn(AndroidSchedulers.mainThread())//最后在主线程中执行
                                    .subscribe(object : Subscriber<Routine>() {
                                        override fun onCompleted() {}
                                        override fun onError(e: Throwable) {}
                                        override fun onNext(task: Routine) {}
                                    })
                        }
                        .negativeText("取消")
                        .onNegative { dialog, _ -> dialog.dismiss() }
                        .show()
                false
            }
        }
    }

    private fun addNormalView(dbRoutine: DBRoutine) {
        val fullHeight = mRes.getDimension(R.dimen.weekly_view_events_height)
        val minuteHeight = fullHeight / (24 * 60)
        val minimalHeight = mRes.getDimension(R.dimen.weekly_view_minimal_event_height).toInt()

        val startDateTime = Formatter.getDateTimeFromTS(dbRoutine.beginTs)
        val endDateTime = Formatter.getDateTimeFromTS(dbRoutine.endTs)
        val dayOfWeek = startDateTime.plusDays(if (context!!.config.isSundayFirst) 1 else 0).dayOfWeek - 1
        val layout = getColumnWithId(dayOfWeek)

        val minTS = Math.max(startDateTime.seconds(), firstDayOfWeek)
        val maxTS = Math.min(endDateTime.seconds(), firstDayOfWeek + WEEK_MILLI_SECONDS)
        if (minTS > maxTS) return

        val startMinutes = startDateTime.minuteOfDay
        val duration = endDateTime.minuteOfDay - startMinutes

        (inflater.inflate(R.layout.week_event_marker, null, false) as TextView).apply {
            val backgroundColor = getColorFromLabel(dbRoutine.label)//eventTypeColors.get(0, primaryColor)
//            background = ColorDrawable(backgroundColor)
            /* 白色带圆角形状的背景 */
            val adImageBackground = GradientDrawable()
            adImageBackground.shape = GradientDrawable.RECTANGLE
            adImageBackground.setColor(backgroundColor)
            adImageBackground.alpha = 200
            adImageBackground.cornerRadius = dp2px(3F).toFloat()
            background = adImageBackground
            setTextColor(backgroundColor.getContrastColor())
            text = dbRoutine.title
            layout.addView(this)
            y = startMinutes * minuteHeight
            (layoutParams as RelativeLayout.LayoutParams).apply {
                width = layout.width - 1
                minHeight = if (dbRoutine.beginTs == dbRoutine.endTs) minimalHeight else (duration * minuteHeight).toInt() - 1
            }
            setOnClickListener {
                Intent(context, InfoOperationActivity::class.java).apply {
                    putExtra(InfoOperationActivity.TO_UPDATE_ROUTINE, dbRoutine)
                    startActivity(this)
                }
            }
            setOnLongClickListener {
                MaterialDialog.Builder(activity!!)
                        .content("确定删除这个任务吗？")
                        .positiveText("删除")
                        .onPositive { _, _ ->
                            try {
                                DB.routines().delete(dbRoutine)
                            } catch (e: SQLException) {
                                e.printStackTrace()
                            }

                            RetrofitHelper.getRoutineService().deleteRoutineByUrl(dbRoutine.getUrl())
                                    .subscribeOn(Schedulers.newThread())//请求在新的线程中执行
                                    .observeOn(AndroidSchedulers.mainThread())//最后在主线程中执行
                                    .subscribe(object : Subscriber<Routine>() {
                                        override fun onCompleted() {}
                                        override fun onError(e: Throwable) {}
                                        override fun onNext(task: Routine) {}
                                    })
                        }
                        .negativeText("取消")
                        .onNegative { dialog, _ -> dialog.dismiss() }
                        .show()
                false
            }
        }
    }

    private fun calculateExtraHeight() {
        mainView.week_top_holder.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (activity == null)
                    return

                mainView.week_top_holder.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (isFragmentVisible) {
                    mListener?.updateHoursTopMargin(mainView.week_top_holder.height)
                }

                if (!wasExtraHeightAdded) {
                    maxScrollY += mainView.week_all_day_holder.height
                    wasExtraHeightAdded = true
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mWasDestroyed = true
    }

    private fun getColumnWithId(id: Int) = mainView.findViewById<ViewGroup>(mRes.getIdentifier("week_column_$id", "id", context!!.packageName))

    fun getColorFromLabel(label: Int) = DBRoutine.labelColor[label]

    fun updateScrollY(y: Int) {
        if (wasFragmentInit) {
            mScrollView.scrollY = y
        }
    }
}
