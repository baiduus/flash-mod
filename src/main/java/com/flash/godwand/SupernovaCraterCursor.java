package com.flash.godwand;

final class SupernovaCraterCursor {
   private final int columns;
   private int visited;
   private int x;
   private int z;
   private int dx;
   private int dz = -1;

   SupernovaCraterCursor(int radius) {
      this.columns = (radius * 2 + 1) * (radius * 2 + 1);
   }

   int x() {
      return this.x;
   }

   int z() {
      return this.z;
   }

   boolean finished() {
      return this.visited >= this.columns;
   }

   void advance() {
      if (!this.finished()) {
         if (this.x == this.z || this.x < 0 && this.x == -this.z || this.x > 0 && this.x == 1 - this.z) {
            int previousDx = this.dx;
            this.dx = -this.dz;
            this.dz = previousDx;
         }

         this.x = this.x + this.dx;
         this.z = this.z + this.dz;
         this.visited++;
      }
   }
}
