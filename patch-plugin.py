# 팝업 없는 모드에서 Google 음성인식 서비스를 우선 사용하도록 플러그인 패치
import re,sys
p='node_modules/@capacitor-community/speech-recognition/android/src/main/java/com/getcapacitor/community/speechrecognition/SpeechRecognition.java'
s=open(p).read()
helper='''
    private SpeechRecognizer createRec() {
        android.content.Context ctx = bridge.getActivity();
        try {
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            java.util.List<android.content.pm.ResolveInfo> l = pm.queryIntentServices(new Intent(android.speech.RecognitionService.SERVICE_INTERFACE), 0);
            for (android.content.pm.ResolveInfo ri : l) {
                if (ri.serviceInfo != null && "com.google.android.googlequicksearchbox".equals(ri.serviceInfo.packageName)) {
                    Logger.info("FactoryTalk", "using Google recognizer " + ri.serviceInfo.name);
                    return SpeechRecognizer.createSpeechRecognizer(ctx, new android.content.ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name));
                }
            }
        } catch (Exception e) { Logger.error("FactoryTalk", "recognizer lookup failed", e); }
        return SpeechRecognizer.createSpeechRecognizer(ctx);
    }
'''
n=s.count('SpeechRecognizer.createSpeechRecognizer(bridge.getActivity())')
s=s.replace('SpeechRecognizer.createSpeechRecognizer(bridge.getActivity())','createRec()')
s=re.sub(r'(public class SpeechRecognition extends Plugin implements Constants \{)', r'\1'+helper.replace('\\','\\\\'), s, count=1)
open(p,'w').write(s)
print('patched',n,'call sites')
