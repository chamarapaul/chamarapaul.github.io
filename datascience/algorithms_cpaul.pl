#!/usr/bin/perl
# sort.pl
#
# Author : Chamara Paul
# Date : 18 March 2004
#
# CPSC 420 Algorithms
# Project 1
#
# Program to test insertion sort, merge-sort, heap-sort, quick-
# sort and radix sort.

use Benchmark;
use POSIX;
use Time::HiRes;

sub COUNTINGSORT
{
	my($k, $d, @C) = @_;
	my(@D, @E);
	my $size = scalar(@C);
	for (my $i = 0; $i < $k; $i++)
	{
		$E[$i] = 0;
	}
	for (my $j = 0; $j < $size ; $j++)
	{
		my $digit = substr $C[$j], -$d, 1;
		$E[$digit] = $E[$digit] + 1;
	}
	for (my $i = 1; $i < $k; $i++)
	{
		$E[$i] = $E[$i] + $E[$i-1];
	}
	for (my $j = $size-1; $j >= 0; $j--)
	{
		my $digit = substr $C[$j], -$d, 1;
		$D[$E[$digit]-1] = $C[$j];
		$E[$digit] = $E[$digit] - 1;
		$opCount++;
	}
	return @D;
}

sub RADIXSORT
{
	my($d, @C) = @_;
	for (my $i = 1; $i <= $d; $i++)
	{
		@C = COUNTINGSORT(10, $i, @C);
	}
	return @C;
}

sub PARTITION
{
	my($p, $r, @C) = @_;
	my($x, $i);
	$x = $C[$r];
	$i = $p - 1;
	for (my $j = $p; $j <= $r - 1; $j++)
	{
		$opCount++;
		if ($C[$j] <= $x)
		{
			$i++;
			my $temp1 = $C[$i];
			$C[$i] = $C[$j];
			$C[$j] = $temp1;
		}
	}
	my $temp2= $C[$i+1];
	$C[$i+1] = $C[$r];
	$C[$r] = $temp2;
	return ($i + 1, @C);
}

sub QUICKSORT
{
	my($p, $r, @C) = @_;
	my $q;
	if ($p < $r)
	{
		($q, @C) = PARTITION($p, $r, @C);
		@C = QUICKSORT($p, $q - 1, @C);
		@C = QUICKSORT($q + 1, $r, @C);
	}
	return @C;
}

sub LEFT
{
	my $i = $_[0];
	return $i * 2 - 1;
}

sub RIGHT
{
	my $i = $_[0];
	return 2 * $i;
}

sub MAXHEAPIFY
{
	my($i, @C) = @_;
	$i++;
	my $l = LEFT($i);
	my $r = RIGHT($i);
	my $size = scalar(@C);
	my $largest;
	if (($l <= $size) && ($C[$l] > $C[$i-1]))
	{
		$largest = $l;
	}
	else
	{
		$largest = $i-1;
	}
	if (($r <= $size) && ($C[$r] > $C[$largest]))
	{
		$largest = $r;
	}
	if ($largest != $i-1)
	{
		my $temp = $C[$i-1];
		$C[$i-1] = $C[$largest];
		$C[$largest] = $temp;
		@C = MAXHEAPIFY($largest, @C);
	}
	$opCount++;
	return @C;
}

sub BUILDMAXHEAP
{
	my @C = @_;
	my $size = scalar(@C)/2;
	for (my $i = $size; $i >= 0; $i--) 
	{
		@C = MAXHEAPIFY($i, @C);
	}
	return @C;
}

sub HEAPSORT
{
	my @C = @_;
	my @D;
	my $size = scalar(@C);
	@C = BUILDMAXHEAP(@C);
	for (my $i = $size; $i > 1; $i--)
	{
	
		my $temp = $C[0];
		$C[0] = $C[$i-1];
		$C[$i-1] = $temp;
		push @D, pop @C;
		@C = MAXHEAPIFY(0, @C);
	}
	push @D, pop @C;
	return @D;
}	

sub MERGE
{
	my($p, $q, $r, @C) = @_;
	my $inf = INT_MAX;
	my $n1 = $q - $p;
	my $n2 = $r - $q - 1;
	my(@R, @L);
	for (my $i = 0; $i <= $n1; $i++)
	{
		$L[$i] = $C[$p+$i];
	}
	for (my $j = 0; $j <= $n2; $j++)
	{
		$R[$j] = $C[$q+1+$j];
	}
	$L[$n1+1] = $inf;
	$R[$n2+1] = $inf;
	my $i = 0;
	my $j = 0;
	for (my $k = $p; $k <= $r; $k++)
	{
		$opCount++;
		if ($L[$i] <= $R[$j])
		{
			$C[$k] = $L[$i];
			$i++;
		}
		else
		{
			$C[$k] = $R[$j];
			$j++;
		}
	} 
	return @C;
}

sub MERGESORT
{
	my($p, $r, @C) = @_;
	if ($p < $r)
	{
		my $q = int(($p + $r)/2);
		@C = MERGESORT($p, $q, @C);
	 	@C = MERGESORT($q+1, $r, @C);
		@C = MERGE($p, $q, $r, @C);
	}
	return @C;
}  

sub INSERTIONSORT
{
	my @C = @_;
	my $size = scalar(@C);
	for (my $j = 1; $j < $size; $j++)
	{
		my $key = $C[$j];
		my $i = $j - 1;
		while (($i >= 0) && ($C[$i] > $key))
		{
			$C[$i+1] = $C[$i];
			$i--;
			$opCount++;
		}
		$C[$i+1] = $key;
	}
	return @C;
}

# Main
my @num = (2, 4, 8, 16, 32, 64, 100, 1000, 10000, 100000, 1000000);
for (my $i = 0; $i < 9; $i++)
{
	my(@A, @B, @C);
	my($time0, $time1);
	for (my $k = 0; $k < 1; $k++)
	{
		for (my $j = 0; $j < $num[$i]; $j++)
		{
			push @A, int(rand 65535);
		}

		@B = @A;
		my $size = scalar(@B);
		
		# Insertion Sort
		$opCount = 0;
		$time0 = new Benchmark;
		@C = INSERTIONSORT(@B);
		$time1 = new Benchmark;
		print "OpCount for Insertion Sort = $opCount\n";
		print "Insertion Sort Elapsed Time for Size $num[$i]: ".
			timestr(timediff($time1, $time0)).
			"\n\n";

		# Merge Sort
		$opCount = 0;
		$time0 = new Benchmark;
		@C = MERGESORT(0, $size, @B);
		$time1 = new Benchmark;
		print "OpCount for Merge Sort = $opCount\n";
		print "Merge Sort Elapsed Time for Size $num[$i]: ".
			timestr(timediff($time1, $time0)).
			"\n\n";

		# Heap Sort
		$opCount = 0;
		$time0 = new Benchmark;
		@C = HEAPSORT(@B);
		$time1 = new Benchmark;
		print "OpCount for Heap Sort = $opCount\n";
		print "Heap Sort Elapsed Time for Size $num[$i]: ".
			timestr(timediff($time1, $time0)).
			"\n\n";

		# Quick Sort
		$opCount = 0;
		$time0 = new Benchmark;
		@C = QUICKSORT(0, $size, @B);
		$time1 = new Benchmark;
		print "OpCount for Quick Sort = $opCount\n";
		print "Quick Sort Elapsed Time for Size $num[$i]: ".
			timestr(timediff($time1, $time0)).
			"\n\n";
	
		# Radix Sort
		$opCount = 0;
		$time0 = new Benchmark;
		@C = RADIXSORT(5, @B);
		$time1 = new Benchmark;
		print "OpCount for Radix Sort = $opCount\n";
		print "Radix Sort Elapsed Time for Size $num[$i]: ".
			timestr(timediff($time1, $time0)).
			"\n\n";
	}
}
print "\n";
