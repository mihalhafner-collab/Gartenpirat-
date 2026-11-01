"""Mandatory fresh-install and real in-place upgrade gate on the isolated emulator.
Never accepts a physical-device serial. Only the original application ID is installed.
"""
import json,subprocess,sys,re,time,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];ADB=ROOT/'android-toolchain/sdk/platform-tools/adb.exe'
TARGET='at.hausgarten.service';SERIAL='emulator-5580'
BUILD=(ROOT/'app/build.gradle').read_text(encoding='utf-8')
VERSION=re.search(r"versionName '([^']+)'",BUILD).group(1)
CODE=int(re.search(r'versionCode (\d+)',BUILD).group(1))
APK=ROOT/('outputs/Haus-und-Gartenservice-Pixel-v'+VERSION+'.apk')
def version(path):return tuple(map(int,re.search(r'-v([\d.]+)\.apk$',path.name).group(1).split('.')))
OLD=max((p for p in (ROOT/'outputs').glob('Haus-und-Gartenservice-Pixel-v*.apk') if version(p)<tuple(map(int,VERSION.split('.')))),key=version)
DIAG=ROOT/'outputs/Pixel-Instrumentation-Architecture.apk'
RESULTS=[]
def adb(*args,timeout=180,check=True):
    if args and args[0]=='install':
        args=('install','--no-incremental',*args[1:])
    p=subprocess.run([str(ADB),'-s',SERIAL,*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=timeout,check=check,
        creationflags=subprocess.CREATE_NO_WINDOW if sys.platform=='win32' else 0)
    return p.stdout.decode('utf-8',errors='replace')
def probe(mode):
    result=adb('shell','am','instrument','-w','-e','release_lifecycle',mode,TARGET+'.test/at.hausgarten.service.PixelMcpInstrumentation')
    (ROOT/'outputs'/('Release-Lifecycle-'+mode+'.log')).write_text(result,encoding='utf-8')
    success='PASS ' in result and 'FAIL ' not in result and 'INSTRUMENTATION_CODE: -1' in result
    RESULTS.append({'scenario':mode,'method':'actual_android_installation','result':'PASS' if success else 'FAIL'})
    if not success:raise RuntimeError('Lifecycle '+mode+' failed. See isolated diagnostic log.')
def main():
    avd=adb('emu','avd','name')
    if not any(name in avd for name in ('HausGarten_QA_Pixel10Pro_API35','HausGarten_Release_QA')):raise RuntimeError('Wrong emulator. No installation changed.')
    if adb('shell','getprop','sys.boot_completed').strip()!='1':raise RuntimeError('Emulator not ready')
    for f in (APK,OLD,DIAG):
        if not f.is_file():raise RuntimeError('Required signed artifact missing: '+f.name)
    # Only this verified disposable emulator may be reset. Never operate on the USB Pixel.
    adb('uninstall',TARGET,check=False);adb('uninstall',TARGET+'.test',check=False)
    try:
        adb('install',str(APK));adb('install','-r','-t',str(DIAG));probe('fresh')
        adb('shell','am','start','-n',TARGET+'/.MainActivity')
        time.sleep(2)
        crash=adb('logcat','-d','-b','crash')
        if 'Process: '+TARGET in crash:raise RuntimeError('Fresh-start crash')
        RESULTS.append({'scenario':'fresh startup','method':'actual_android_activity','result':'PASS'})
        adb('uninstall',TARGET+'.test');adb('uninstall',TARGET)
        adb('install',str(OLD));adb('install','-r','-t',str(DIAG));probe('prepare')
        # This MUST be -r: no uninstall, data clearing or replacement database.
        adb('install','-r',str(APK));probe('verify')
        code=adb('shell','dumpsys','package',TARGET)
        if not re.search(r'versionCode='+str(CODE)+r'\b',code):raise RuntimeError('Wrong upgraded version')
        RESULTS.append({'scenario':'same package/versionCode installed','method':'actual_android_package_manager','result':'PASS'})
        native=adb('shell','am','instrument','-w',TARGET+'.test/at.hausgarten.service.PixelMcpInstrumentation',timeout=360)
        (ROOT/('outputs/Release-Native-v'+VERSION+'.log')).write_text(native,encoding='utf-8')
        success='RESULT ' in native and 'FAILED ' not in native and 'FAIL ' not in native
        RESULTS.append({'scenario':'complete native regression and billing','method':'actual_android_sqlite_mcp','result':'PASS' if success else 'FAIL','passed':len(re.findall(r'^PASS ',native,re.M))})
        if not success:raise RuntimeError('Native regression failed')
    finally:
        adb('uninstall',TARGET+'.test',check=False)
        (ROOT/('outputs/Release-Lifecycle-v'+VERSION+'.json')).write_text(json.dumps({'serial':SERIAL,'api':35,'target_sdk':37,'version':VERSION,'version_code':CODE,'apk_sha256':hashlib.sha256(APK.read_bytes()).hexdigest(),
            'productive_device_modified':False,'results':RESULTS,'success':bool(RESULTS) and all(x['result']=='PASS' for x in RESULTS)},indent=2),encoding='utf-8')
    print(json.dumps(RESULTS))
if __name__=='__main__':main()
