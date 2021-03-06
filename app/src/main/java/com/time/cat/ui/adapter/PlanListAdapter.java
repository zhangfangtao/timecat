package com.time.cat.ui.adapter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.time.cat.R;
import com.time.cat.TimeCatApp;
import com.time.cat.data.database.DB;
import com.time.cat.data.model.DBmodel.DBPlan;
import com.time.cat.data.model.events.PersistenceEvents;
import com.time.cat.ui.modules.main.MainActivity;
import com.time.cat.ui.modules.plans.detail.PlanDetailActivity;
import com.time.cat.ui.modules.routines.RoutinesFragment;
import com.time.cat.util.override.ToastUtil;
import com.time.cat.util.string.TimeUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author dlink
 * @email linxy59@mail2.sysu.edu.cn
 * @date 2018/3/16
 * @discription null
 * @usage null
 */
public class PlanListAdapter extends RecyclerView.Adapter<PlanListAdapter.ViewHolder> implements RoutinesFragment.OnScrollBoundaryDecider{
    private List<DBPlan> dataSet;
    private Activity activity;
    private int position;

    public PlanListAdapter(List<DBPlan> dbPlanList, Activity activity) {
        dataSet = (dbPlanList == null) ? new ArrayList<>() : dbPlanList;
        this.activity = activity;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (activity == null) {
            activity = (MainActivity) parent.getContext();
        }
        View itemView = LayoutInflater.from(activity).inflate(R.layout.base_list_item, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, final int position) {
        final DBPlan dbPlan = dataSet.get(position);
        holder.base_tv_title.setText(dbPlan.getTitle());
        holder.base_tv_content.setText(dbPlan.getContent());
        Date date = TimeUtil.formatGMTDateStr(dbPlan.getCreated_datetime());
        if (date != null) {
            holder.base_tv_time.setText(date.toLocaleString());
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, PlanDetailActivity.class);
                Bundle bundle = new Bundle();
                bundle.putSerializable("DBPlan", dbPlan);
                intent.putExtras(bundle);
                activity.startActivity(intent);
            }
        });

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                new MaterialDialog.Builder(activity)
                        .content("确定删除这个计划吗？")
                        .positiveText("删除")
                        .onPositive((dialog, which) -> {
                            try {
                                DB.plans().delete(dbPlan);
                                ToastUtil.ok("已删除");
                            } catch (SQLException e) {
                                e.printStackTrace();
                                ToastUtil.e("删除失败");
                            }
                            Object event = new PersistenceEvents.NoteDeleteEvent();
                            TimeCatApp.eventBus().post(event);
                        })
                        .negativeText("取消")
                        .onNegative((dialog, which) -> dialog.dismiss()).show();
                return false;
            }
        });
    }

    @Override
    public int getItemCount() {
        return dataSet.size();
    }

    @Override
    public boolean canRefresh() {
        return false;
    }

    @Override
    public boolean canLoadMore() {
        return false;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView base_tv_title, base_tv_content, base_tv_time;

        public ViewHolder(View itemView) {
            super(itemView);
            base_tv_title = itemView.findViewById(R.id.base_tv_title);
            base_tv_content = itemView.findViewById(R.id.base_tv_content);
            base_tv_time = itemView.findViewById(R.id.base_tv_time);
        }
    }
}
