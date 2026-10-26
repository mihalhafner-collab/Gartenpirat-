"""Native presentation checks; no Android/device/account access."""
import re
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'android/app/src/main/java/at/hausgarten/service'

class NativeUiConsistencyTests(unittest.TestCase):
    def test_colors_match_shared_web_tokens(self):
        css=(ROOT/'android/app/src/main/assets/tokens.css').read_text()
        native=(JAVA/'NativeUi.java').read_text()
        colors=dict(re.findall(r'\b([A-Z_]+)=0xff([a-f0-9]{6})',native))
        tokens=dict(re.findall(r'--hg-([a-z-]+):#([a-f0-9]{6})',css))
        self.assertEqual(set(colors),{k.replace('-','_').upper() for k in tokens})
        for key,value in colors.items():self.assertEqual(value,tokens[key.lower().replace('_','-')],key)
    def test_native_controls_use_shared_styles_and_touch_targets(self):
        for name in ['PlanningConnectionActivity','DriveConnectionActivity','PixelTunnelActivity']:
            source=(JAVA/(name+'.java')).read_text()
            self.assertNotIn('new CheckBox(',source)
            self.assertEqual(source.count('new Button(this)'),source.count('NativeUi.style(new Button(this),'))
            self.assertEqual(source.count('new Switch(this)'),source.count('NativeUi.toggle(new Switch(this))'))
        helper=(JAVA/'NativeUi.java').read_text()
        for fragment in ['setMinHeight(dp(c,52))','setMinHeight(dp(toggle.getContext(),56))','setStateListAnimator(null)','setElevation(0)']:
            self.assertIn(fragment,helper)
    def test_original_accessible_captions_remain_available(self):
        source=(JAVA/'PlanningConnectionActivity.java').read_text()
        for text in ['Google-Freigabe erneuern','Kalender-App öffnen','Konto und Freigaben','Wetterwarnungen','Pixel-Benachrichtigungen','Auch Belegtzeiten meines Hauptkalenders prüfen']:
            self.assertIn(text,source)
