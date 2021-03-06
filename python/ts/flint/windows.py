#
#  Copyright 2017-2018 TWO SIGMA OPEN SOURCE, LLC
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

'''This module contains window constructors suitable as arguments to:

- :meth:`TimeSeriesDataFrame.addWindows`
- :meth:`TimeSeriesDataFrame.summarizeWindows`

Example:

    >>> from ts.flint import windows
    >>> prices.addWindows(windows.past_absolute_time('1d'), key='id')

'''

from . import java
from abc import abstractmethod, ABCMeta


__all__ = [
    'past_absolute_time',
    'future_absolute_time',
]


class WindowsFactoryBase:
    '''WindowFactory represents an intended window that will be
    instantiated later when we have access to a SparkContext.

    Typical usage is that a user constructs one of these (using the
    functions in this module), then passes it to one of the
    window-related methods of :class:`TimeSeriesDataFrame`, where we
    have a SparkContext.  In those methods, we have this factory
    construct the actual window object the user wanted.

    '''
    __metaclass__ = ABCMeta

    def __init__(self, func, *args):
        self._func = func
        self._args = args

    @abstractmethod
    def _java_cls(self, sc):
        pass

    def _jwindow(self, sc):
        return self._java_cls(sc).__getattr__(self._func)(*self._args)

    def __str__(self):
        return "%s(%s)" % (self._func, ", ".join(str(arg) for arg in self._args))


class WindowsFactory(WindowsFactoryBase):
    def __init__(self, func, *args):
        super().__init__(func, *args)

    def _java_cls(self, sc):
        return java.Packages(sc).Windows


def past_absolute_time(duration):
    '''Creates a window over a fixed amount of time into the past.

    The ``duration`` should be specified as a time string with units,
    such as ``'5 days'`` or ``'100s'``.  These strings are interpreted
    by |scala_concurrent_duration_Duration|_, so the rules there
    apply.  Importantly:

    .. epigraph::

       Format is ``"<length><unit>"``, where whitespace is allowed
       before, between and after the parts.  Infinities are designated
       by ``"Inf"``, ``"PlusInf"``, ``"+Inf"`` and ``"-Inf"`` or
       ``"MinusInf"``.

       Valid choices [for unit] are:

       ``d``, ``day``, ``h``, ``hour``, ``min``, ``minute``, ``s``,
       ``sec``, ``second``, ``ms``, ``milli``, ``millisecond``,
       ``??s``, ``micro``, ``microsecond``, ``ns``, ``nano``,
       ``nanosecond`` and their pluralized forms (for every but the
       first mentioned form of each unit, i.e. no "ds", but "days").

    Example:

        >>> prices.addWindows(windows.past_absolute_time('1d'), key='id')

    :param duration: the size of the window, as a string with units
    :type duration: str

    '''

    return WindowsFactory('pastAbsoluteTime', duration)


def future_absolute_time(duration):
    '''Creates a window over a fixed amount of time into the future.

    The ``duration`` should be specified as a time string with units,
    such as ``'5 days'`` or ``'100s'``.  These strings are interpreted
    by |scala_concurrent_duration_Duration|_, so the rules there
    apply.  Importantly:

    .. epigraph::

       Format is ``"<length><unit>"``, where whitespace is allowed
       before, between and after the parts.  Infinities are designated
       by ``"Inf"``, ``"PlusInf"``, ``"+Inf"`` and ``"-Inf"`` or
       ``"MinusInf"``.

       Valid choices [for unit] are:

       ``d``, ``day``, ``h``, ``hour``, ``min``, ``minute``, ``s``,
       ``sec``, ``second``, ``ms``, ``milli``, ``millisecond``,
       ``??s``, ``micro``, ``microsecond``, ``ns``, ``nano``,
       ``nanosecond`` and their pluralized forms (for every but the
       first mentioned form of each unit, i.e. no "ds", but "days").

    Example:

        >>> prices.addWindows(windows.future_absolute_time('1d'), key='id')

    :param duration: the size of the window, as a string with units
    :type duration: str

    '''


    return WindowsFactory('futureAbsoluteTime', duration)
