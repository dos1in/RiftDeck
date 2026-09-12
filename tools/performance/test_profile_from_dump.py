import unittest
from profile_from_dump import convert, descriptor


class ProfileConversionTest(unittest.TestCase):
    def test_descriptors(self):
        self.assertEqual('[[I', descriptor('int[][]'))
        self.assertEqual('[Ljava/lang/String;', descriptor('java.lang.String[]'))
        self.assertEqual('V', descriptor('void'))

    def test_flags_merge_and_inline_cache_is_excluded(self):
        result = convert(['\thot methods:',
                          '\t\tvoid com.riftdeck.Test.run(int, java.lang.String[])[0x1:cache], ',
                          '\tstartup methods:',
                          '\t\tvoid com.riftdeck.Test.run(int, java.lang.String[])',
                          '\tpost startup methods:',
                          '\t\tvoid com.riftdeck.Test.run(int, java.lang.String[])'])
        self.assertEqual('HSPLcom/riftdeck/Test;->run(I[Ljava/lang/String;)V\n', result)

    def test_constructor_class_and_dependency_filter(self):
        result = convert(['\thot methods:', '\t\tvoid com.riftdeck.Test$Inner.<init>()[],',
                          '\t\tvoid androidx.Test.run()[]', '\tclasses:',
                          '\t\tcom.riftdeck.Test$Inner', '\t\tandroidx.Test'])
        self.assertEqual('Lcom/riftdeck/Test$Inner;\nHLcom/riftdeck/Test$Inner;-><init>()V\n', result)

    def test_rejects_empty_and_malformed_application_method(self):
        with self.assertRaises(ValueError):
            convert([])
        with self.assertRaises(ValueError):
            convert(['\thot methods:', '\t\tvoid com.riftdeck.Test.malformed'])


if __name__ == '__main__':
    unittest.main()
