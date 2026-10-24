import importlib.util
import pathlib
import tempfile
import unittest
import sys

sys.dont_write_bytecode = True

spec = importlib.util.spec_from_file_location("cpu", pathlib.Path(__file__).with_name("validate-emulator-cpu.py"))
cpu = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cpu)


class CpuEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.report = pathlib.Path(self.temporary.name)
        self.write("effective-cpu-evidence.txt", "requested_cores=4\n--- /canonical/test.avd/config.ini\n")
        self.write("canonical-avd-config.ini", "hw.cpu.ncore=4\n")
        self.write("cpu-online-mask.txt", "0-3\n")
        self.write("cpu-online-count.txt", "4\n")
        self.write("cpu-processors.txt", "".join(f"processor : {i}\n" for i in range(4)))

    def write(self, name, value):
        (self.report / name).write_text(value)

    def test_accepts_legal_ini_whitespace(self):
        for line in ["hw.cpu.ncore=4", "hw.cpu.ncore = 4", " \thw.cpu.ncore\t=\t4 \t"]:
            with self.subTest(line=line):
                self.write("canonical-avd-config.ini", line + "\n")
                cpu.validate(self.report)

    def test_rejects_wrong_missing_similar_and_duplicate_config_keys(self):
        for config in ["", "hw.cpu.ncore=2", "hwXcpuXncore=4", "hw.cpu.ncore.extra=4",
                       "hw.cpu.ncore=4\nhw.cpu.ncore=4", "hw.cpu.ncore=4\n hw.cpu.ncore = 2"]:
            with self.subTest(config=config):
                self.write("canonical-avd-config.ini", config)
                with self.assertRaisesRegex(ValueError, "expected.*observed"):
                    cpu.validate(self.report)

    def test_rejects_missing_multiple_and_duplicate_canonical_configs(self):
        for paths in ["", "--- /one\n--- /two\n", "--- /one\n--- /one\n"]:
            with self.subTest(paths=paths):
                self.write("effective-cpu-evidence.txt", "requested_cores=4\n" + paths)
                with self.assertRaisesRegex(ValueError, "canonical AVD configs"):
                    cpu.validate(self.report)

    def test_rejects_wrong_or_ambiguous_requested_cores(self):
        for requested in ["", "requested_cores=2\n", "requested_cores=4\nrequested_cores=4\n"]:
            with self.subTest(requested=requested):
                self.write("effective-cpu-evidence.txt", requested + "--- /canonical/config.ini\n")
                with self.assertRaisesRegex(ValueError, "requested cores"):
                    cpu.validate(self.report)

    def test_rejects_wrong_guest_mask(self):
        self.write("cpu-online-mask.txt", "0-1")
        with self.assertRaisesRegex(ValueError, "guest online CPU mask"):
            cpu.validate(self.report)

    def test_rejects_wrong_guest_count(self):
        self.write("cpu-online-count.txt", "2")
        with self.assertRaisesRegex(ValueError, "guest online CPU count"):
            cpu.validate(self.report)

    def test_rejects_missing_duplicate_and_unexpected_processors(self):
        for ids in [[0, 1, 2], [0, 1, 2, 2], [0, 1, 2, 3, 4], [0, 1, 2, 4]]:
            with self.subTest(ids=ids):
                self.write("cpu-processors.txt", "".join(f"processor : {i}\n" for i in ids))
                with self.assertRaisesRegex(ValueError, "guest processor IDs"):
                    cpu.validate(self.report)


if __name__ == "__main__":
    unittest.main()
