/*
 *  Monero Miner App (c) 2018 Uwe Post
 *  based on the XMRig Monero Miner https://github.com/xmrig/xmrig
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program. If not, see <http://www.gnu.org/licenses/>.
 * /
 */

package de.ludetis.monerominer;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final String LOG_TAG = "main";
    private String privatePath;
    private Process process;
    private ScheduledExecutorService svc;
    private TextView tvLog;
    private OutputReaderThread outputHandler;
    private EditText edPool,edUser;
    private EditText edCmdline;
    private String configTemplate;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // path where we may execute our program
        privatePath =  getFilesDir().getAbsolutePath() ;

        // copy binaries to a path where we may execute it);
        Tools.copyFile(this,"xmrig-arm64",privatePath+ "/xmrig");
        Tools.copyFile(this,"libuv.so",privatePath+ "/libuv.so");
        Tools.copyFile(this,"libc++_shared.so",privatePath+ "/libc++_shared.so");

        // load config template
        configTemplate = Tools.loadConfigTemplate(this);

        // wire views
        tvLog = findViewById(R.id.output);
        edPool = findViewById(R.id.pool);
        edUser = findViewById(R.id.username);

        findViewById(R.id.start).setOnClickListener(this::startMining);
        findViewById(R.id.help).setOnClickListener(this::startMining);
        findViewById(R.id.stop).setOnClickListener(this::stopMining);

        // check architecture
        tvLog.setText("cpu architecture: " + Tools.getCPUInfo().get("CPU_architecture"));
        if(!"aarch64".equalsIgnoreCase(Tools.getCPUInfo().get("CPU_architecture"))) {
            Toast.makeText(this,"Sorry, this app currently only supports AARCH64 architecture!", Toast.LENGTH_SHORT).show();
            findViewById(R.id.start).setEnabled(false);
        }

        // the executor which will load and display xmrig's output
        svc = Executors.newSingleThreadScheduledExecutor();
        svc.scheduleWithFixedDelay(this::updateLog, 1, 1, TimeUnit.SECONDS);

    }


    private void stopMining(View view) {
        if(process !=null) {
            process.destroy();
            process = null;
            Log.i(LOG_TAG, "stopped");
        }
    }

    private void startMining(View view) {
        Log.i(LOG_TAG,"starting...");
        if(process !=null) {
            process.destroy();
        }

        // just for convenience, the help command
        String[] args = {"./xmrig" };
        String[] mineh = { "./xmrig","--help"};
        if(view.getId()==R.id.help) args = mineh;

        try {
            // write the config
            Tools.writeConfig(configTemplate,edPool.getText().toString(), edUser.getText().toString(),privatePath);

            // run xmrig
            ProcessBuilder pb = new ProcessBuilder(args);
            // in our directory
            pb.directory(getApplicationContext().getFilesDir());
            // with the directory as ld path so xmrig finds the libs
            pb.environment().put("LD_LIBRARY_PATH",  privatePath );
            // in case of errors, read them
            pb.redirectErrorStream();
            // run it!
            process = pb.start();
            // start processing xmrig's output
            outputHandler = new OutputReaderThread(process.getInputStream());
            outputHandler.start();

        } catch (Exception e) {
            Log.e(LOG_TAG,"exception:",e);
            Toast.makeText(this,e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            process = null;
        }

    }


    private void updateLog() {
        runOnUiThread(()->{
            if(outputHandler!=null && outputHandler.output!=null) {
                tvLog.setText(outputHandler.output.toString());
            }
        });
    }


    /**
     * thread to collect the binary's output
     */
    private static class OutputReaderThread extends Thread {

        private InputStream inputStream;
        private StringBuilder output = new StringBuilder();

        OutputReaderThread(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        public void run() {
            try {
                int c;
                while ((c = inputStream.read()) != -1) {
                    output.append((char) c);
                }
            } catch (IOException e) {
                Log.w(LOG_TAG,"exception",e);
            }
        }

        public StringBuilder getOutput() {
            return output;
        }

    }



}
