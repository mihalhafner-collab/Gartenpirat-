"""Small, valid image fixture shared by disposable photo-storage tests."""
import base64


VALID_GIF_BASE64 = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
VALID_GIF = base64.b64decode(VALID_GIF_BASE64)
VALID_GIF_DATA_URI = "data:image/gif;base64," + VALID_GIF_BASE64
