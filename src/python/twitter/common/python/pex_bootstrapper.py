import contextlib
import os
import zipfile

__all__ = ('bootstrap_pex',)


def pex_info_name(entry_point):
  """Return the PEX-INFO for an entry_point"""
  return os.path.join(entry_point, 'PEX-INFO')


def is_compressed(entry_point):
  return os.path.exists(entry_point) and not os.path.exists(pex_info_name(entry_point))


def read_pexinfo_from_directory(entry_point):
  with open(pex_info_name(entry_point), 'rb') as fp:
    return fp.read()


def read_pexinfo_from_zip(entry_point):
  with contextlib.closing(zipfile.ZipFile(entry_point)) as zf:
    return zf.read('PEX-INFO')


def read_pex_info_content(entry_point):
  """Return the raw content of a PEX-INFO."""
  if is_compressed(entry_point):
    return read_pexinfo_from_zip(entry_point)
  else:
    return read_pexinfo_from_directory(entry_point)


def get_pex_info(entry_point):
  """Return the PexInfo object for an entry point."""
  from . import pex_info

  pex_info_content = read_pex_info_content(entry_point)
  if pex_info_content:
    return pex_info.PexInfo.from_json(pex_info_content)
  raise ValueError('Invalid entry_point: %s' % entry_point)


def bootstrap_pex(entry_point):
  from . import pex
  pex.PEX(entry_point).execute()
