import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

s=importlib.util.spec_from_file_location('research_java',Path(__file__).with_name('evaluate_candidates.py'))
m=importlib.util.module_from_spec(s);s.loader.exec_module(m)


class JavaCandidateTests(unittest.TestCase):
    def test_actual_compiled_java_causality_and_guard_rails(self):
        with tempfile.TemporaryDirectory() as td:
            classes,_=m.compile_java(Path(td))
            subprocess.run(['java','-cp',str(classes),
                'dev.jboehm.vectorvio.pdr.research.StepResearchSelfTest'],check=True)


if __name__=='__main__':unittest.main()
