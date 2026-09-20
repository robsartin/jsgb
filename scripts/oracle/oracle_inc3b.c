#include "gb_graph.h"
#include "gb_io.h"
#include "gb_basic.h"
#include "gb_rand.h"
#include "gb_save.h"
#include "gb_raman.h"
#define is_boolean(v) (((siz_t)(v))<=1)
static void pr_vert();static void pr_arc();static void pr_util();
static void print_sample(g,n) Graph*g;int n;
{printf("\n");
if(g==NULL){printf("Ooops, we just ran into panic code %ld!\n",panic_code);
if(io_errors)printf("(The I/O error code is 0x%lx)\n",(unsigned long)io_errors);}
else{printf("\"%s\"\n%ld vertices, %ld arcs, util_types %s",g->id,g->n,g->m,g->util_types);
pr_util(g->uu,g->util_types[8],0,g->util_types);pr_util(g->vv,g->util_types[9],0,g->util_types);
pr_util(g->ww,g->util_types[10],0,g->util_types);pr_util(g->xx,g->util_types[11],0,g->util_types);
pr_util(g->yy,g->util_types[12],0,g->util_types);pr_util(g->zz,g->util_types[13],0,g->util_types);
printf("\n");printf("V%d: ",n);
if(n>=g->n||n<0)printf("index is out of range!\n");
else{pr_vert(g->vertices+n,1,g->util_types);printf("\n");}
gb_recycle(g);}}
static void pr_vert(v,l,s) Vertex*v;int l;char*s;
{if(v==NULL)printf("NULL");else if(is_boolean(v))printf("ONE");
else{printf("\"%s\"",v->name);pr_util(v->u,s[0],l-1,s);pr_util(v->v,s[1],l-1,s);pr_util(v->w,s[2],l-1,s);
pr_util(v->x,s[3],l-1,s);pr_util(v->y,s[4],l-1,s);pr_util(v->z,s[5],l-1,s);
if(l>0){register Arc*a;for(a=v->arcs;a;a=a->next){printf("\n   ");pr_arc(a,1,s);}}}}
static void pr_arc(a,l,s) Arc*a;int l;char*s;
{printf("->");pr_vert(a->tip,0,s);if(l>0){printf(", %ld",a->len);pr_util(a->a,s[6],l-1,s);pr_util(a->b,s[7],l-1,s);}}
static void pr_util(u,c,l,s) util u;char c;int l;char*s;
{switch(c){case'I':printf("[%ld]",u.I);break;case'S':printf("[\"%s\"]",u.S?u.S:"(null)");break;
case'A':if(l<0)break;printf("[");if(u.A==NULL)printf("NULL");else pr_arc(u.A,l,s);printf("]");break;
case'V':if(l<0)break;printf("[");pr_vert(u.V,l,s);printf("]");default:break;}}
#include "gb_books.h"
#include "gb_econ.h"
#include "gb_games.h"
#include "gb_lisa.h"
#include "gb_gates.h"
static unsigned long memry[34]={0x2ff0,0x1111,0x1a30,0x3333,0x7f70,0x5555,0x0f8f,0x3a21,0x1a01,0x0a12,0x3a01,0x4000,0x5000,0x6000,0x2a63,0x0f95,0x3063,0x1061,0x6ac1,0x5fd1,0x2a63,0x039b,0x0843,0x3463,0x1561,0x2863,0x0c94,0x4861,0x6ac1,0x2a63,0x5a41,0x0398,0x6666,0x0fa7};
int main(){Graph*g;long*a;long k;char buf[200];Area area;setvbuf(stdout,NULL,_IONBF,0);
printf("==book_anna\n");print_sample(book("anna",50L,10L,1L,10L,1L,1L,1L),3);
printf("==book_david\n");print_sample(book("david",30L,5L,0L,0L,1L,1L,5L),0);
printf("==bi_book_jean\n");print_sample(bi_book("jean",100L,0L,1L,5L,1L,1L,2L),82);
printf("==bi_book_homer\n");print_sample(bi_book("homer",100L,0L,10L,20L,1L,1L,3L),105);
printf("==book_bad\n");print_sample(book("huck",0L,0L,0L,0L,2000000L,0L,1L),0);
printf("==book_chapters\n");g=book("huck",20L,0L,0L,0L,1L,1L,4L);printf("chapters=%ld first=%s last=%s\n",chapters,chap_name[1],chap_name[chapters]);print_sample(g,0);
printf("==econ_full\n");print_sample(econ(81L,0L,0L,1L),0);
printf("==econ_omit2\n");print_sample(econ(40L,2L,1000L,5L),5);
printf("==econ_greedy\n");print_sample(econ(10L,1L,0L,0L),2);
printf("==econ_users\n");print_sample(econ(20L,0L,0L,7L),19);
printf("==econ_default\n");print_sample(econ(0L,0L,0L,0L),80);
printf("==games_full\n");print_sample(games(120L,0L,0L,0L,0L,0L,0L,1L),0);
printf("==games_window\n");print_sample(games(30L,1L,1L,1L,1L,60L,90L,7L),3);
printf("==games_neg\n");print_sample(games(10L,-1L,-1L,-1L,-1L,-5L,0L,3L),0);
printf("==games_bad\n");print_sample(games(5L,200000L,0L,0L,0L,0L,0L,1L),0);
printf("==lisa_matrix\n");init_area(area);a=lisa(4L,4L,255L,0L,0L,0L,0L,0L,0L,area);printf("%s\n",lisa_id);for(k=0;k<16;k++)printf("%ld%c",a[k],k%4==3?'\n':' ');gb_free(area);
printf("==lisa_window\n");init_area(area);a=lisa(3L,5L,7L,100L,110L,100L,110L,1000L,60000L,area);printf("%s\n",lisa_id);for(k=0;k<15;k++)printf("%ld%c",a[k],k%5==4?'\n':' ');gb_free(area);
printf("==plane_lisa_small\n");print_sample(plane_lisa(20L,20L,10L,0L,0L,0L,0L,0L,0L),0);
printf("==plane_lisa_window\n");print_sample(plane_lisa(0L,0L,0L,100L,110L,100L,110L,0L,0L),3);
printf("==bi_lisa\n");print_sample(bi_lisa(10L,10L,0L,0L,0L,0L,30000L,0L),0);
printf("==bi_lisa_c\n");print_sample(bi_lisa(10L,10L,100L,110L,100L,110L,20000L,1L),12);
printf("==lisa_bad\n");print_sample(plane_lisa(5L,5L,0L,10L,10L,0L,0L,0L,0L),0);
printf("==risc2_sample\n");print_sample(risc(2L),1);
g=risc(2L);printf("==risc2_eval=%ld\n",gate_eval(g,"10000000000000001",buf));printf("%s\n",buf);gb_recycle(g);
g=risc(8L);memry[1]=3;memry[3]=4;memry[5]=10;printf("==run_risc_mult\n");printf("return=%ld\n",run_risc(g,memry,34L,8L));for(k=0;k<18;k++)printf("%lu ",risc_state[k]);printf("\n");
memry[5]=7;printf("==run_risc_div\n");printf("return=%ld\n",run_risc(g,memry,34L,0L));for(k=0;k<18;k++)printf("%lu ",risc_state[k]);printf("\n");gb_recycle(g);
printf("==prod22\n");g=prod(2L,2L);print_gates(g);print_sample(g,0);
printf("==prod33_sample\n");print_sample(prod(3L,3L),10);
printf("==partial_prod33\n");g=partial_gates(prod(3L,3L),2L,50000L,1L,buf);printf("%s\n",buf);print_sample(g,5);
printf("==partial_risc_stanza4\n");g=partial_gates(risc(0L),1L,43210L,98765L,buf);printf("%s\n",buf);print_sample(g,79);
printf("==prod_bad\n");print_sample(partial_gates(NULL,1L,1L,1L,NULL),0);
printf("==risc2_gates\n");g=risc(2L);print_gates(g);gb_recycle(g);
return 0;}
