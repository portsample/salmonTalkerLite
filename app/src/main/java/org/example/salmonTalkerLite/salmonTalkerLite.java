// Copyright 2019 Alpha Cephei Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.example.salmonTalkerLite;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class salmonTalkerLite extends Activity implements
        RecognitionListener {

    static private final int iSTATE_START = 0;
    static private final int iSTATE_READY = 1;
    static private final int iSTATE_DONE = 2;
    static private final int iSTATE_FILE = 3;
    static private final int iSTATE_MIC = 4;

    /* Used to handle permission request */
    private static final int iPERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private Model model;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private TextView tvStatus, tvSpecies, tvCount;

    public String szVoskOutput = " ";

    public String szSpecies = " ";

    ToggleButton tbtnStartStop;
    Button btnReadMe;

    //txt2int stuff start

    boolean bIsValidInput = true;
    long lResult = 0;
    long lFinalResult = 0;
    List<String> szlAllowedStrings = Arrays.asList
            (
                    "zero","one","two","three","four","five","six","seven",
                    "eight","nine","ten","eleven","twelve","thirteen","fourteen",
                    "fifteen","sixteen","seventeen","eighteen","nineteen","twenty",
                    "thirty","forty","fifty","sixty","seventy","eighty","ninety",
                    "hundred","thousand","million","billion","trillion"
            );

    String szInput = "  ";
    String szFinalResult = "  ";

    //txt2int stuff end...

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.main);
        // Setup layout
        tvStatus = findViewById(R.id.status);
        tvSpecies = findViewById(R.id.result_species);
        tvCount = findViewById(R.id.result_count);

        tbtnStartStop = (ToggleButton) this.findViewById(R.id.btnstartstopxml);
        tbtnStartStop.setOnClickListener(view -> recognizeMicrophone());

        btnReadMe = (Button) this.findViewById(R.id.btnreadmexml);
        btnReadMe.setOnClickListener((view ->readMe()));

        setUiState(iSTATE_START);
        LibVosk.setLogLevel(LogLevel.INFO);

        // Check if user has given permission to record audio, init the model after permission is granted
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, iPERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            initModel();
        }
    }
    private void setUiState(int state) {
        switch (state) {
            case iSTATE_START:
                tvStatus.setText(R.string.preparing);
                findViewById(R.id.btnstartstopxml).setEnabled(false);
                break;
            case iSTATE_READY:
                tvStatus.setText(R.string.ready);
                ((Button) findViewById(R.id.btnstartstopxml)).setText(R.string.szstartxml);
                findViewById(R.id.btnstartstopxml).setEnabled(true);
                break;
            case iSTATE_DONE:
                tvStatus.setText(R.string.ready);
                ((Button) findViewById(R.id.btnstartstopxml)).setText(R.string.szstartxml);
                findViewById(R.id.btnstartstopxml).setEnabled(true);
                break;
            case iSTATE_MIC:
                tvStatus.setText(R.string.say_something);
                ((Button) findViewById(R.id.btnstartstopxml)).setText(R.string.szstopxml);
                findViewById(R.id.btnstartstopxml).setEnabled(true);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + state);
        }
    }//end setUiState()

    private void initModel() {
        StorageService.unpack(this, "model-en-us", "model",
                (model) -> {
                    this.model = model;
                    setUiState(iSTATE_READY);
                },
                (exception) -> setErrorState("Failed to unpack the model" + exception.getMessage()));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == iPERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initModel();
            } else {
                finish();
            }
        }
    }

    private void recognizeMicrophone() {
       // String szKeywords="[\"sockeye pink coho chum chinook salmon oh zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty thirty forty fifty sixty seventy eighty ninety hundred thousand million billion trillion\\\",\\\"[unk]\\\"]";
      String szKeywords = "[\"one\",\"two\",\"three\",\"four\",\"red\",\"[unk]\"]";
       // List<String> szlKeywords = Arrays.asList("SOCKEYE", "SOCKEYE SALMON", "RED SALMON", "RED", "REDS");

        if (speechService != null) {
            setUiState(iSTATE_DONE);
            speechService.stop();
            speechService = null;
        } else {
            setUiState(iSTATE_MIC);
            try {
               // Recognizer rec = new Recognizer(model, 16000.0f);
                //These are the only words passed by the speech engine
                 //Recognizer rec = new Recognizer(model, 16000, "[\"sockeye pink coho chum chinook salmon oh zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty thirty forty fifty sixty seventy eighty ninety hundred thousand million billion trillion\",\"[unk]\"]");
                Recognizer rec = new Recognizer(model, 16000, szKeywords);
                      //both of the below string formats work.
                //Recognizer rec = new Recognizer(model, 16000, "[\"one two three four red\",\"[unk]\"]");
                //Recognizer rec = new Recognizer(model, 16000, "[\"one\",\"two\",\"three\",\"four\",\"red\",\"[unk]\"]");


                // Recognizer rec = new Recognizer(model, 16000.f, "[\"one zero zero zero one\", " +
                   //     "\"oh zero one two three four five six seven eight nine\", \"[unk]\"]");

                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    @Override
    public void onPartialResult(String s) {
   }

    @Override
    public void onResult(String s){
        szVoskOutput = "";
        try {
            JSONObject o = new JSONObject(s);
            if (o.has("text")) {
                szVoskOutput = o.getString("text");
            }
        }
        catch (JSONException ignored) {
        }
        parseWords();
    }

/************************************************************************************************
 *     This is txt2int code to translate numbers ("one hundred and four") into digits ("104").
 ************************************************************************************************/
    public void parseNumbers(){
        if(szInput != null && szInput.length()> 0)
        {
            szInput = szInput.replaceAll("-", " ");
            szInput = szInput.replaceAll("OH", "ZERO");
            szInput = szInput.toLowerCase().replaceAll(" and", " ");
            String[] splittedParts = szInput.trim().split("\\s+");

            for(String str : splittedParts)
            {
                if(!szlAllowedStrings.contains(str))
                {
                    bIsValidInput = false;
                    Toast.makeText(this, "Invalid word found:" + str, Toast.LENGTH_LONG).show();
                    //System.out.println("Invalid word found : "+str);
                    //return;
                    break;
                }
            }
            if(bIsValidInput)
            {
                for(String str : splittedParts)
                {
                    if(str.equalsIgnoreCase("zero")) {
                        lResult += 0;
                    }
                    else if(str.equalsIgnoreCase("one")) {
                        lResult += 1;
                    }
                    else if(str.equalsIgnoreCase("two")) {
                        lResult += 2;
                    }
                    else if(str.equalsIgnoreCase("three")) {
                        lResult += 3;
                    }
                    else if(str.equalsIgnoreCase("four")) {
                        lResult += 4;
                    }
                    else if(str.equalsIgnoreCase("five")) {
                        lResult += 5;
                    }
                    else if(str.equalsIgnoreCase("six")) {
                        lResult += 6;
                    }
                    else if(str.equalsIgnoreCase("seven")) {
                        lResult += 7;
                    }
                    else if(str.equalsIgnoreCase("eight")) {
                        lResult += 8;
                    }
                    else if(str.equalsIgnoreCase("nine")) {
                        lResult += 9;
                    }
                    else if(str.equalsIgnoreCase("ten")) {
                        lResult += 10;
                    }
                    else if(str.equalsIgnoreCase("eleven")) {
                        lResult += 11;
                    }
                    else if(str.equalsIgnoreCase("twelve")) {
                        lResult += 12;
                    }
                    else if(str.equalsIgnoreCase("thirteen")) {
                        lResult += 13;
                    }
                    else if(str.equalsIgnoreCase("fourteen")) {
                        lResult += 14;
                    }
                    else if(str.equalsIgnoreCase("fifteen")) {
                        lResult += 15;
                    }
                    else if(str.equalsIgnoreCase("sixteen")) {
                        lResult += 16;
                    }
                    else if(str.equalsIgnoreCase("seventeen")) {
                        lResult += 17;
                    }
                    else if(str.equalsIgnoreCase("eighteen")) {
                        lResult += 18;
                    }
                    else if(str.equalsIgnoreCase("nineteen")) {
                        lResult += 19;
                    }
                    else if(str.equalsIgnoreCase("twenty")) {
                        lResult += 20;
                    }
                    else if(str.equalsIgnoreCase("thirty")) {
                        lResult += 30;
                    }
                    else if(str.equalsIgnoreCase("forty")) {
                        lResult += 40;
                    }
                    else if(str.equalsIgnoreCase("fifty")) {
                        lResult += 50;
                    }
                    else if(str.equalsIgnoreCase("sixty")) {
                        lResult += 60;
                    }
                    else if(str.equalsIgnoreCase("seventy")) {
                        lResult += 70;
                    }
                    else if(str.equalsIgnoreCase("eighty")) {
                        lResult += 80;
                    }
                    else if(str.equalsIgnoreCase("ninety")) {
                        lResult += 90;
                    }
                    else if(str.equalsIgnoreCase("hundred")) {
                        lResult *= 100;
                    }
                    else if(str.equalsIgnoreCase("thousand")) {
                        lResult *= 1000;
                        lFinalResult += lResult;
                        lResult=0;
                    }
                    else if(str.equalsIgnoreCase("million")) {
                        lResult *= 1000000;
                        lFinalResult += lResult;
                        lResult=0;
                    }
                    else if(str.equalsIgnoreCase("billion")) {
                        lResult *= 1000000000;
                        lFinalResult += lResult;
                        lResult=0;
                    }
                    else if(str.equalsIgnoreCase("trillion")) {
                        lResult *= 1000000000000L;
                        lFinalResult += lResult;
                        lResult=0;
                    }
                }

                lFinalResult += lResult;
                lResult=0;
                szFinalResult = Long.toString(lFinalResult);
                //System.out.println(lFinalResult);
                //toast out final result
     //           Toast.makeText(this, "Captured txt2int string is:" + lFinalResult, Toast.LENGTH_SHORT).show();
                lFinalResult=0;
            }else{//not valid
                Toast.makeText(this, "Bottom elseif, not valid.", Toast.LENGTH_SHORT).show();
                bIsValidInput = true;
                //return;
            }
        }
    }//end of parseNumbers
    /********************************* parseWords() *******************************************
 *  this is where we can maniipulate the output to be either a number, or a salmon species.
 *  There are 5 species being counted. The arrays include colloquial names and a variety of
 *  phonemes.
 ******************************************************************************************/
    public void parseWords() {
     //   List<String> szlNumbers = Arrays.asList(new String[]{"ONE", "TEN", "ONE HUNDRED", "ONE THOUSAND", "TEN THOUSAND"});//this is going to get bigger...
        //species synonyms and variations
        List<String> szlChinook = Arrays.asList("CHINOOK", "CHINOOK SALMON", "KING", "KINGS", "KING SALMON");
        List<String> szlSockeye = Arrays.asList("SOCKEYE", "SOCKEYE SALMON", "RED SALMON", "RED", "REDS");
        List<String> szlCoho = Arrays.asList("COHO", "COHO SALMON", "SILVER SALMON", "SILVER", "SILVERS");
        List<String> szlPink = Arrays.asList("PINK", "PINK", "PINKS", "PINK SALMON", "HUMPY");
        List<String> szlChum = Arrays.asList("CHUM", "CHUM SALMON", "KETA SALMON", "KETA", "DOG", "DOGS", "DOG SALMON", "GATOR", "GATORS", "CALICO");
        List<String> szlAtlantic = Arrays.asList("ATLANTIC", "ATLANTICS", "ATLANTIC SALMON");

        //Collections.sort(szlSpecies);
        szVoskOutput=szVoskOutput.toUpperCase();

        if (szVoskOutput.compareTo("")==0){
            //do nothing, this is a blank string
            return;
        }
        if(szVoskOutput==null){//...and this is a null string
            return;
        }
        //pink
        if (szlPink.contains(szVoskOutput)) {
            szSpecies = "Pink";
            populateSpecies();
            return;
        }
        //chum
        if (szlChum.contains(szVoskOutput)) {
            szSpecies = "Chum";
            populateSpecies();
            return;
        }
        //sockeye
        if (szlSockeye.contains(szVoskOutput)) {
            szSpecies = "Sockeye";
            populateSpecies();
            return;
        }
        //coho
        if (szlCoho.contains(szVoskOutput)) {
            szSpecies = "Coho";
            populateSpecies();
            return;
        }
        //Chinook
        if (szlChinook.contains(szVoskOutput)) {
            szSpecies = "Chinook";
            populateSpecies();
            return;
        }
        //Atlantic
        if (szlAtlantic.contains(szVoskOutput)) {
            szSpecies = "Atlantic";
            populateSpecies();
            return;
  /*     }
        if(szlNumbers.contains(szVoskOutput)) {//then this is a number, put in count txt box
           tvCount.setText(szVoskOutput);
           return;
      */  }else{
            szInput=szVoskOutput;
            parseNumbers();
            tvCount.setText(szFinalResult);
               // Toast.makeText(this, "Please repeat clearly. Captured string is:" + szVoskOutput, Toast.LENGTH_SHORT).show();
        }
    }//end parseWords()

    @Override
    public void onFinalResult(String szHypothesis) {
        //why do we need below???
        setUiState(iSTATE_DONE);
        if (speechStreamService != null) {
            speechStreamService = null;
        }
    }

    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
    }
    @Override
    public void onTimeout() {
        setUiState(iSTATE_DONE);
    }

    private void setErrorState(String szErrorMessage) {
        tvStatus.setText(szErrorMessage);
        ((Button) findViewById(R.id.btnstartstopxml)).setText(R.string.szstartxml);
        findViewById(R.id.btnstartstopxml).setEnabled(false);
    }

    private void pause(boolean bChecked) {
        if (speechService != null) {
            speechService.setPause(bChecked);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }
        if (speechStreamService != null) {
            speechStreamService.stop();
        }
    }

    public void populateSpecies(){
        tvSpecies.setText(szSpecies);
        tvCount.setText("---"); //sets count field to null.
        return;
    }
    /************************************************************************************
     * readMe()  opens a Dialog widget that briefly describes this application.
     ***************************************************************************************/
    public void readMe() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Instructions: Press START. Clearly say the name of the salmon species you wish to count. The name can be Chinook, sockeye, coho, pink, chum or Atlantic. Wait for it to display in the species field. Then clearly say the number of fish observed. The number must be 1, 10, 100, 1000, or 10,000. Wait for it to display in the count field. Rejected words will momentarily show at the bottom of the screen. A larger version of this application (SalmonTalker) can be found in the Google Play store. \nThis application utilizes an offline speech recognition model and is intended as proof-of-concept. Future versions will have a significantly greater vocabulary and be much, much faster.");
        builder.setPositiveButton("Onward", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}//end of everything
