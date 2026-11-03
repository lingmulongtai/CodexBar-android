#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("guard", Path(__file__).with_name("verify-release-workmanager.py"))
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class MinifiedMergerTests(unittest.TestCase):
    def dump(self, method=""):
        return "Class #3 -\n  Class descriptor : 'Landroidx/work/OverwritingInputMerger;'\n  Direct methods -\n" + method + "  Virtual methods -\n"

    def test_rejects_the_actual_stripped_release_shape(self):
        self.assertFalse(guard.retains_merger_constructor(self.dump()))

    def test_accepts_public_no_argument_constructor(self):
        method = "    #7 : (in Landroidx/work/OverwritingInputMerger;)\n      name : '<init>'\n      type : '()V'\n      access : 0x10001 (PUBLIC CONSTRUCTOR)\n"
        self.assertTrue(guard.retains_merger_constructor(self.dump(method)))

    def test_rejects_parameterized_or_non_public_constructor(self):
        for signature, access in [("(I)V", "0x10001"), ("()V", "0x10002")]:
            method = f"    #0 :\n      name : '<init>'\n      type : '{signature}'\n      access : {access}\n"
            self.assertFalse(guard.retains_merger_constructor(self.dump(method)))

    def test_constructor_of_a_different_class_does_not_satisfy_the_gate(self):
        method = "    #0 :\n      name : '<init>'\n      type : '()V'\n      access : 0x10001\n"
        other = self.dump(method).replace("OverwritingInputMerger", "Other")
        self.assertFalse(guard.retains_merger_constructor(self.dump() + other))


if __name__ == "__main__":
    unittest.main()
