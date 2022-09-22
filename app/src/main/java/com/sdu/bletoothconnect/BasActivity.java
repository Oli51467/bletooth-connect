package com.sdu.bletoothconnect;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Calendar;

public class BasActivity extends Activity {

    private String ERROR = "exception";
    public String AS = "AppRunTime";
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.currentThread().setUncaughtExceptionHandler(new MyUncaughtExceptionHandler());
    }

    private class MyUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        //当有未捕获的异常的时候会调用
        @Override
        public void uncaughtException(@NonNull Thread thread, Throwable ex) {
            //将异常保存到文件中
            try {
                //异常文件log.txt，可以判断返回给我们的服务器
                ex.printStackTrace(new PrintStream(new File("log.txt")));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            Writer w = new StringWriter();
            ex.printStackTrace(new PrintWriter(w));
            String smsg = w.toString();
            saveNew(smsg);
            smsg += "     -------这是一处错误(%&*@#$)";
            Log.e(ERROR, smsg);
            save(smsg);
            //保存文件之后，自杀,myPid() : 获取自己的pid
            android.os.Process.killProcess(android.os.Process.myPid());
        }

        public void save(String inputText) {
            FileOutputStream out = null;
            BufferedWriter writer = null;
            try {
                out = openFileOutput("errlog", MODE_APPEND | Context.MODE_PRIVATE);
                writer = new BufferedWriter(new OutputStreamWriter(out));
                writer.write(inputText);
                out.write("\r\n".getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (writer != null) {
                        writer.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void saveNew(String inputText) {
            FileOutputStream out = null;
            BufferedWriter writer = null;
            try {
                out = openFileOutput("errNewLog", Context.MODE_PRIVATE);
                writer = new BufferedWriter(new OutputStreamWriter(out));
                out.write("\r\n".getBytes());
                writer.write(inputText + "------最新异常" + getTime());
                out.write("\r\n".getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (writer != null) {
                        writer.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String getTime() {
        //获取系统的 日期
        Calendar calendar = Calendar.getInstance();
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        return month + "月-" + day + "日-" + hour + "时-" + minute + "分-" + second + "秒";
    }
}
