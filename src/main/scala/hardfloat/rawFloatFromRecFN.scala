
/*============================================================================

This Chisel source file is part of a pre-release version of the HardFloat IEEE
Floating-Point Arithmetic Package, by John R. Hauser (with some contributions
from Yunsup Lee and Andrew Waterman, mainly concerning testing).

Copyright 2010, 2011, 2012, 2013, 2014, 2015, 2016 The Regents of the
University of California.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice,
    this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions, and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

 3. Neither the name of the University nor the names of its contributors may
    be used to endorse or promote products derived from this software without
    specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS "AS IS", AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, ARE
DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

=============================================================================*/

package hardfloat

import chisel3._
import chisel3.util._

/*----------------------------------------------------------------------------
| In the result, no more than one of 'isNaN', 'isInf', and 'isZero' will be
| set.
*----------------------------------------------------------------------------*/
object rawFloatFromRecFN
{
    def apply(expWidth: Int, sigWidth: Int, in: Bits): RawFloat =
    {
        val exp = in(expWidth + sigWidth - 1, sigWidth - 1)
        val isZero    = exp(expWidth, expWidth - 2) === 0.U
        val isSpecial = exp(expWidth, expWidth - 1) === 3.U

        val out = Wire(new RawFloat(expWidth, sigWidth))
        out.isNaN  := isSpecial &&   exp(expWidth - 2)
        out.isInf  := isSpecial && ! exp(expWidth - 2)
        out.isZero := isZero
        out.sign   := in(expWidth + sigWidth) // original
        // out.sign   := in(expWidth + sigWidth-1 ) // Edward added the -1
        out.sExp   := exp.zext
        out.sig    := 0.U(1.W) ## ! isZero ## in(sigWidth - 2, 0)
        out
    }
}
