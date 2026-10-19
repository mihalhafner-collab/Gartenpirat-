"""Persist reproducible test results; every test uses disposable data fixtures."""
import argparse
import json
import sys
import unittest
from pathlib import Path


class RecordingResult(unittest.TextTestResult):
    def __init__(self,*args,**kwargs):
        super().__init__(*args,**kwargs)
        self.records=[]
    def addSuccess(self,test):
        super().addSuccess(test);self.records.append({'test':test.id(),'result':'PASS'})
    def addFailure(self,test,error):
        super().addFailure(test,error);self.records.append({'test':test.id(),'result':'FAIL','error':self._exc_info_to_string(error,test)})
    def addError(self,test,error):
        super().addError(test,error);self.records.append({'test':test.id(),'result':'ERROR','error':self._exc_info_to_string(error,test)})
    def addSkip(self,test,reason):
        super().addSkip(test,reason);self.records.append({'test':test.id(),'result':'SKIP','reason':reason})
    def addSubTest(self,test,subtest,error):
        super().addSubTest(test,subtest,error)
        if error is not None:
            self.records.append({'test':str(subtest),'result':'FAIL','error':self._exc_info_to_string(error,test)})


if __name__=='__main__':
    root=Path(__file__).resolve().parents[1]
    # Running this file directly puts only tests/ on sys.path.  Make the project
    # root explicit so a clean QA run never depends on the caller's environment.
    if str(root) not in sys.path:
        sys.path.insert(0, str(root))
    import runtime_paths
    runtime_paths.synthetic_only()
    parser=argparse.ArgumentParser()
    parser.add_argument('--exclude-qa-signing',action='store_true',help='Exclude legacy tests that generate real temporary signing keys.')
    parser.add_argument('--artifact',help='Actual release artifact to include in the final privacy gate.')
    args=parser.parse_args()
    from privacy_guard import source_gate,workspace_gate,artifact_gate
    privacy_before={'source':source_gate(),'workspace':workspace_gate()}
    suite=unittest.defaultTestLoader.discover(str(root/'tests'))
    excluded=[]
    if args.exclude_qa_signing:
        def filtered(items):
            for item in items:
                if isinstance(item,unittest.TestSuite):yield from filtered(item)
                elif item.__class__.__module__=='test_qa_signing':excluded.append(item.id())
                else:yield item
        suite=unittest.TestSuite(filtered(suite))
    result=unittest.TextTestRunner(resultclass=RecordingResult,verbosity=1).run(suite)
    privacy_after={'source':source_gate(),'workspace':workspace_gate(),
                   'artifact':artifact_gate(args.artifact) if args.artifact else 'NOT TESTABLE'}
    (root/'outputs'/'QA-Python-results.json').write_text(json.dumps({'executions':result.testsRun,
        'success':result.wasSuccessful(),'privacy_before':privacy_before,'privacy_after':privacy_after,'explicitly_excluded':excluded,'skips':len(result.skipped),'results':result.records},ensure_ascii=False,indent=2),encoding='utf-8')
    raise SystemExit(0 if result.wasSuccessful() else 1)
