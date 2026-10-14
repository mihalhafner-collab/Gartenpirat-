"""Reproducible, isolated app audit; no imports/repairs of productive business data.

Runs only the explicitly allow-listed diagnostic tests. Never starts production,
installs an APK, connects Drive, changes credentials or invokes repair scripts.
"""
import ast
import json
import os
import re
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT/'outputs'/('architecture-audit-'+datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%SZ'))
RUN.mkdir(parents=True, exist_ok=False)
ENV = {**os.environ, 'PYTHONUTF8':'1', 'PYTHONPATH':str(ROOT)}
FLAGS = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
RESULTS = []

def execute(name, args, timeout=300, env=None):
    started = time.monotonic()
    try:
        done = subprocess.run(args, cwd=ROOT, env=env or ENV, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, timeout=timeout, creationflags=FLAGS)
        log = done.stdout.decode('utf-8', errors='replace')
        record = {'suite':name, 'method':'actually_executed', 'exit_code':done.returncode,
            'result':'PASS' if done.returncode == 0 else 'FAIL', 'seconds':round(time.monotonic()-started,2)}
    except (subprocess.TimeoutExpired, OSError) as error:
        log = str(error); record = {'suite':name,'method':'actually_attempted','result':'BLOCKED'}
    (RUN/(name+'.log')).write_text(log,encoding='utf-8')
    RESULTS.append(record)
    print(json.dumps(record), flush=True)
    return record

def inventory():
    sources = [*ROOT.glob('*.py'), *ROOT.glob('mcp_integration/*.py'), *ROOT.glob('static/*'),
        *ROOT.glob('android/app/src/main/assets/*'), *ROOT.glob('android/app/src/main/java/**/*.java'),
        ROOT/'android/app/src/main/AndroidManifest.xml', ROOT/'android/app/build.gradle']
    rows=[]
    for file in sorted(set(sources)):
        if file.suffix not in {'.py','.js','.java','.html','.css','.xml','.gradle','.webmanifest'}:continue
        source=file.read_text(encoding='utf-8')
        functions=[]
        if file.suffix=='.py':
            functions=[n.name for n in ast.walk(ast.parse(source)) if isinstance(n,(ast.FunctionDef,ast.AsyncFunctionDef))]
        else:
            functions=re.findall(r'\bfunction\s+(\w+)\s*\(',source)
            if file.suffix=='.java':functions+=re.findall(r'\b(?:public|private|protected|static|synchronized)\s+(?:static\s+)?[\w<>\[\]]+\s+(\w+)\s*\(',source)
        rows.append({'file':str(file.relative_to(ROOT)), 'lines':len(source.splitlines()),'functions':functions,
            'statically_inventoried':True, 'runtime_coverage':'See suite results; inventory is NOT a runtime test'})
    (RUN/'source-inventory.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')

def start_fixture(script, log_name, pattern, env=None):
    log_path=RUN/log_name
    handle=log_path.open('wb')
    process=subprocess.Popen([sys.executable,script],cwd=ROOT,env=env or ENV,
        stdout=handle,stderr=subprocess.STDOUT,creationflags=FLAGS)
    try:
        end=time.monotonic()+30
        while time.monotonic()<end:
            found=re.search(pattern,log_path.read_text(encoding='utf-8',errors='replace'))
            if found:return process,handle,found.group(1)
            if process.poll() is not None:raise RuntimeError('Fixture failed to start; see '+log_name)
            time.sleep(.1)
        raise RuntimeError('Fixture startup timed out; see '+log_name)
    except BaseException:
        process.terminate();process.wait(timeout=10);handle.close();raise

def main():
    inventory()
    execute('python-regression',[sys.executable,'tests/run_quality_suite.py'])
    execute('mcp-stdio-e2e',[sys.executable,'tests/mcp_stdio_e2e.py'])
    # Process-scoped only: do not change the user's Windows execution policy.
    execute('native-jvm-contracts',['powershell','-NoProfile','-ExecutionPolicy','Bypass','-File','tests/run-pixel-tunnel-contract-tests.ps1'])
    for name in ['invoice-flow.test.js','invoice-editor.test.js','pixel-tunnel-ui.test.js',
        'design-concept.test.cjs','simple-design.test.cjs','architecture-workflow.test.cjs',
        'work-location.test.cjs','worklog-window.test.cjs','timer-completion.test.cjs','customer-duplicate-ui.test.cjs','gartenpirat-removal.test.cjs','design-v139.test.cjs','daily-usability.test.cjs','billing-ui.test.cjs']:
        execute(name.replace('.','-'),['node','tests/'+name],180)
    fixtures=[]
    try:
        laptop=start_fixture('tests/release_qa_server.py','laptop-fixture.log',r'QA laptop: (http://127\.0\.0\.1:\d+)',{**ENV,'QA_PORT':'0'});fixtures.append(laptop)
        mobile=start_fixture('tests/mobile_invoice_preview_server.py','mobile-fixture.log',r'Android UI QA: (http://127\.0\.0\.1:\d+)');fixtures.append(mobile)
        execute('browser-end-to-end',['node','tests/release-browser.test.js'],300,
            {**ENV,'QA_LAPTOP_URL':laptop[2],'QA_MOBILE_URL':mobile[2]})
    except Exception as error:
        RESULTS.append({'suite':'browser-end-to-end','result':'BLOCKED','reason':str(error)})
    finally:
        for process,handle,_ in reversed(fixtures):
            process.terminate()
            try:process.wait(timeout=10)
            except subprocess.TimeoutExpired:process.kill();process.wait()
            handle.close()
    for filename in ['QA-Python-results.json','QA-MCP-E2E.json','QA-Browser-results.json']:
        source=ROOT/'outputs'/filename
        if source.exists():(RUN/filename).write_bytes(source.read_bytes())
    summary={'log_time_zone':'UTC','user_time_zone':'Europe/Vienna','business_data_write_tests':'isolated fixtures only',
        'productive_connection_tested':False,'real_device_test':'Separate USB instrumentation run required',
        'suites':RESULTS,'success':all(r['result']=='PASS' for r in RESULTS)}
    (RUN/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print('AUDIT_REPORT '+str(RUN/'summary.json'),flush=True)
    return 0 if summary['success'] else 1

if __name__=='__main__':raise SystemExit(main())
